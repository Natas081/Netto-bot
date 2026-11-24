package com.linkguard.client;

import com.linkguard.TestSupport;
import com.linkguard.TestSupport.MutableClock;
import com.linkguard.config.VirusTotalProperties;
import com.linkguard.dto.VirusTotalReport;
import com.linkguard.exception.VirusTotalException;
import com.linkguard.exception.VirusTotalException.Reason;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VirusTotalClientTest {

    private static final String BASE = "https://www.virustotal.com/api/v3";
    private static final URI URL = URI.create("https://example.com/");
    private final MutableClock clock = new MutableClock();
    private VirusTotalProperties properties;
    private VirusTotalRateLimiter limiter;
    private MockRestServiceServer server;
    private VirusTotalClient client;

    @BeforeEach
    void setUp() {
        properties = TestSupport.properties("test-key");
        limiter = new VirusTotalRateLimiter(properties, clock);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new VirusTotalClient(builder.build(), properties, limiter, clock);
    }

    private String reportUrl() {
        return BASE + "/urls/" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(URL.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String reportJson(String stats) {
        return """
                {"data":{"type":"url","attributes":{"last_analysis_stats":%s,"last_analysis_date":%d}}}
                """.formatted(stats, clock.instant().getEpochSecond());
    }

    @Test
    void retrievesReportWithExactCountsAndBackendAuthentication() {
        server.expect(requestTo(reportUrl())).andExpect(method(HttpMethod.GET))
                .andExpect(header("x-apikey", "test-key"))
                .andRespond(withSuccess(reportJson("""
                        {"malicious":2,"suspicious":3,"harmless":61,"undetected":9,"timeout":1}
                        """), MediaType.APPLICATION_JSON));
        VirusTotalReport report = client.getReport(URL).orElseThrow();
        assertThat(report.stats().malicious()).isEqualTo(2);
        assertThat(report.stats().suspicious()).isEqualTo(3);
        assertThat(report.stats().harmless()).isEqualTo(61);
        assertThat(report.stats().undetected()).isEqualTo(9);
        assertThat(report.analyzedAt()).isEqualTo(clock.instant());
        server.verify();
    }

    @Test
    void missingReportIsSubmittedAsFormData() {
        server.expect(requestTo(reportUrl())).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE + "/urls")).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("url=https%3A%2F%2Fexample.com%2F"))
                .andRespond(withSuccess("""
                        {"data":{"type":"analysis","id":"u-analysis-1"}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.getReport(URL)).isEmpty();
        assertThat(client.submit(URL)).isEqualTo("u-analysis-1");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"queued", "in-progress"})
    void pendingAnalysisDoesNotExposePartialCountsAsFinal(String status) {
        server.expect(requestTo(BASE + "/analyses/u-pending"))
                .andRespond(withSuccess("""
                        {"data":{"type":"analysis","attributes":{"status":"%s","stats":{}}}}
                        """.formatted(status), MediaType.APPLICATION_JSON));
        var analysis = client.getAnalysis("u-pending");
        assertThat(analysis.completed()).isFalse();
        assertThat(analysis.report()).isNull();
        server.verify();
    }

    @Test
    void readsCompletedAnalysis() {
        server.expect(requestTo(BASE + "/analyses/u-done")).andRespond(withSuccess("""
                {"data":{"type":"analysis","attributes":{"status":"completed","date":%d,
                "stats":{"malicious":0,"suspicious":1,"harmless":60,"undetected":12}}}}
                """.formatted(clock.instant().getEpochSecond()), MediaType.APPLICATION_JSON));
        var analysis = client.getAnalysis("u-done");
        assertThat(analysis.completed()).isTrue();
        assertThat(analysis.report().stats().suspicious()).isEqualTo(1);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"401,UNAUTHORIZED", "403,UNAUTHORIZED", "429,RATE_LIMIT", "500,UNAVAILABLE", "503,UNAVAILABLE"})
    void translatesApiErrorsWithoutLeakingTheirBody(int status, Reason reason) {
        server.expect(requestTo(reportUrl())).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("sensitive upstream diagnostic").contentType(MediaType.TEXT_PLAIN));
        assertThatThrownBy(() -> client.getReport(URL)).isInstanceOfSatisfying(VirusTotalException.class, ex -> {
            assertThat(ex.reason()).isEqualTo(reason);
            assertThat(ex.getMessage()).doesNotContain("sensitive", "test-key");
        });
        server.verify();
    }

    @Test
    void honorsRetryAfterWithoutIssuingAnotherHttpRequest() {
        server.expect(requestTo(reportUrl())).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "120"));
        assertThatThrownBy(() -> client.getReport(URL)).isInstanceOf(VirusTotalException.class);
        clock.advance(Duration.ofSeconds(61));
        assertThatThrownBy(() -> client.getReport(URL)).isInstanceOfSatisfying(VirusTotalException.class,
                ex -> assertThat(ex.reason()).isEqualTo(Reason.RATE_LIMIT));
        server.verify();
    }

    @Test
    void handlesNetworkFailureAndTimeout() {
        server.expect(requestTo(reportUrl())).andRespond(withException(new IOException("timeout")));
        assertThatThrownBy(() -> client.getReport(URL)).isInstanceOfSatisfying(VirusTotalException.class,
                ex -> assertThat(ex.reason()).isEqualTo(Reason.UNAVAILABLE));
        server.verify();
    }

    @Test
    void missingKeyPreventsAllHttpRequests() {
        VirusTotalProperties absentKey = TestSupport.properties("");
        client = new VirusTotalClient(RestClient.create(), absentKey, limiter, clock);
        assertThatThrownBy(() -> client.getReport(URL)).isInstanceOfSatisfying(VirusTotalException.class,
                ex -> assertThat(ex.reason()).isEqualTo(Reason.NOT_CONFIGURED));
        assertThat(absentKey.toString()).doesNotContain("test-key");
        server.verify();
    }

    @Test
    void invalidHeaderCharactersCannotExposeTheKeyThroughAnHttpException() {
        VirusTotalProperties invalidKey = TestSupport.properties("secret\r\nheader");
        client = new VirusTotalClient(RestClient.create(), invalidKey, limiter, clock);
        assertThatThrownBy(() -> client.getReport(URL)).isInstanceOfSatisfying(VirusTotalException.class, ex -> {
            assertThat(ex.reason()).isEqualTo(Reason.UNAUTHORIZED);
            assertThat(ex.getMessage()).doesNotContain("secret");
        });
        assertThat(invalidKey.toString()).doesNotContain("secret");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"malicious\":0,\"suspicious\":0,\"harmless\":-1,\"undetected\":1}",
            "{\"malicious\":0,\"suspicious\":0,\"harmless\":2}",
            "{\"malicious\":0,\"suspicious\":0,\"harmless\":2.5,\"undetected\":1}"
    })
    void malformedStatisticsNeverManufactureZeroCounts(String stats) {
        server.expect(requestTo(reportUrl())).andRespond(withSuccess(reportJson(stats), MediaType.APPLICATION_JSON));
        if ("{}".equals(stats)) {
            assertThat(client.getReport(URL).orElseThrow().stats()).isNull();
        } else {
            assertThatThrownBy(() -> client.getReport(URL)).isInstanceOfSatisfying(VirusTotalException.class,
                    ex -> assertThat(ex.reason()).isEqualTo(Reason.INVALID_RESPONSE));
        }
        server.verify();
    }

    @Test
    void invalidJsonIsHandledWithoutExposingParserDetails() {
        server.expect(requestTo(reportUrl())).andRespond(withSuccess("{", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getReport(URL)).isInstanceOf(VirusTotalException.class);
        server.verify();
    }
}
