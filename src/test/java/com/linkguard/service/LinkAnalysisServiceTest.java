package com.linkguard.service;

import com.linkguard.TestSupport;
import com.linkguard.TestSupport.MutableClock;
import com.linkguard.client.VirusTotalClient;
import com.linkguard.config.VirusTotalProperties;
import com.linkguard.dto.ChatResponse;
import com.linkguard.dto.ChatResponse.Status;
import com.linkguard.dto.ScanStats;
import com.linkguard.dto.VirusTotalAnalysis;
import com.linkguard.dto.VirusTotalReport;
import com.linkguard.exception.InvalidUrlException;
import com.linkguard.exception.VirusTotalException;
import com.linkguard.exception.VirusTotalException.Reason;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LinkAnalysisServiceTest {

    private static final URI URL = URI.create("https://example.com/");
    private final MutableClock clock = new MutableClock();
    private final VirusTotalClient client = mock(VirusTotalClient.class);
    private LinkAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new LinkAnalysisService(new UrlValidationService(), client, new LinkClassificationService(clock),
                TestSupport.properties("test-key"), clock);
    }

    private VirusTotalReport cleanReport() {
        return new VirusTotalReport(new ScanStats(0, 0, 64, 11), clock.instant());
    }

    private ChatResponse startPendingAnalysis() {
        when(client.getReport(URL)).thenReturn(Optional.empty());
        when(client.submit(URL)).thenReturn("u-test-analysis");
        return service.analyze(URL.toString());
    }

    @Test
    void returnsAnExistingRealReportAndCachesIt() {
        when(client.getReport(URL)).thenReturn(Optional.of(cleanReport()));
        ChatResponse first = service.analyze(URL.toString());
        assertThat(first.status()).isEqualTo(Status.CONFIÁVEL);
        assertThat(first.stats().harmless()).isEqualTo(64);
        assertThat(first.stats().undetected()).isEqualTo(11);
        assertThat(service.analyze("https://example.com")).isEqualTo(first);
        verify(client, times(1)).getReport(URL);
        verify(client, never()).submit(any());
    }

    @Test
    void expiresCachedReports() {
        when(client.getReport(URL)).thenReturn(Optional.of(cleanReport()));
        service.analyze(URL.toString());
        clock.advance(Duration.ofMinutes(16));
        service.analyze(URL.toString());
        verify(client, times(2)).getReport(URL);
    }

    @Test
    void staleOrUnclassifiedReportsRequestANewAnalysis() {
        when(client.getReport(URL)).thenReturn(Optional.of(new VirusTotalReport(
                new ScanStats(0, 0, 64, 11), clock.instant().minus(Duration.ofDays(2)))));
        when(client.submit(URL)).thenReturn("u-new");
        assertThat(service.analyze(URL.toString()).status()).isEqualTo(Status.EM_ANALISE);
        verify(client).submit(URL);
    }

    @Test
    void onlyUndetectedEnginesRequestANewAnalysis() {
        when(client.getReport(URL)).thenReturn(Optional.of(new VirusTotalReport(
                new ScanStats(0, 0, 0, 75), clock.instant())));
        when(client.submit(URL)).thenReturn("u-new");
        assertThat(service.analyze(URL.toString()).status()).isEqualTo(Status.EM_ANALISE);
    }

    @Test
    void pollsOnlyAfterTheIntervalAndUpdatesTheOriginalTicket() {
        ChatResponse pending = startPendingAnalysis();
        assertThat(pending.status()).isEqualTo(Status.EM_ANALISE);
        assertThat(pending.analysisId()).isNotNull();
        assertThat(pending.stats()).isNull();
        assertThat(service.poll(pending.analysisId()).status()).isEqualTo(Status.EM_ANALISE);
        verify(client, never()).getAnalysis(any());
        clock.advance(Duration.ofSeconds(21));
        when(client.getAnalysis("u-test-analysis")).thenReturn(new VirusTotalAnalysis("completed", cleanReport()));
        ChatResponse result = service.poll(pending.analysisId());
        assertThat(result.status()).isEqualTo(Status.CONFIÁVEL);
        assertThat(service.poll(pending.analysisId())).isEqualTo(result);
        assertThat(service.analyze(URL.toString())).isEqualTo(result);
        verify(client, times(1)).getAnalysis("u-test-analysis");
        verify(client, times(1)).submit(URL);
    }

    @Test
    void stopsAfterTheBoundedNumberOfPolls() {
        ChatResponse pending = startPendingAnalysis();
        when(client.getAnalysis("u-test-analysis")).thenReturn(new VirusTotalAnalysis("queued", null));
        for (int i = 0; i < 6; i++) {
            clock.advance(Duration.ofSeconds(21));
            assertThat(service.poll(pending.analysisId()).status()).isEqualTo(Status.EM_ANALISE);
        }
        assertThat(service.poll(pending.analysisId()).status()).isEqualTo(Status.INCONCLUSIVO);
        verify(client, times(6)).getAnalysis("u-test-analysis");
    }

    @Test
    void stopsFollowingAnAnalysisAfterTheDeadline() {
        ChatResponse pending = startPendingAnalysis();
        clock.advance(Duration.ofMinutes(5));
        assertThat(service.poll(pending.analysisId()).status()).isEqualTo(Status.INCONCLUSIVO);
        verify(client, never()).getAnalysis(any());
    }

    @Test
    void completedAnalysisWithoutVerdictsRemainsInconclusive() {
        ChatResponse pending = startPendingAnalysis();
        when(client.getAnalysis("u-test-analysis")).thenReturn(new VirusTotalAnalysis("completed",
                new VirusTotalReport(new ScanStats(0, 0, 0, 75), clock.instant())));
        clock.advance(Duration.ofSeconds(21));
        ChatResponse result = service.poll(pending.analysisId());
        assertThat(result.status()).isEqualTo(Status.INCONCLUSIVO);
        assertThat(result.stats().undetected()).isEqualTo(75);
        assertThat(result.stats().harmless()).isZero();
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void everyProviderFailureIsInconclusiveAndCached(Reason reason) {
        when(client.getReport(URL)).thenThrow(new VirusTotalException(reason));
        ChatResponse response = service.analyze(URL.toString());
        assertThat(response.status()).isEqualTo(Status.INCONCLUSIVO);
        assertThat(response.reply()).contains("não significa que o link seja seguro");
        if (reason == Reason.NOT_CONFIGURED) assertThat(response.reply()).contains("VIRUSTOTAL_API_KEY");
        if (reason == Reason.RATE_LIMIT) assertThat(response.reply()).contains("limite");
        assertThat(response.stats()).isNull();
        assertThat(service.analyze(URL.toString())).isEqualTo(response);
        verify(client, times(1)).getReport(URL);
        verify(client, never()).submit(any());
    }

    @Test
    void pollingFailureAlsoBecomesInconclusive() {
        ChatResponse pending = startPendingAnalysis();
        when(client.getAnalysis("u-test-analysis")).thenThrow(new VirusTotalException(Reason.RATE_LIMIT));
        clock.advance(Duration.ofSeconds(21));
        assertThat(service.poll(pending.analysisId()).status()).isEqualTo(Status.INCONCLUSIVO);
    }

    @Test
    void invalidOrPrivateUrlsNeverReachVirusTotal() {
        assertThatThrownBy(() -> service.analyze("http://192.168.1.1")).isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() -> service.analyze("Olá")).isInstanceOf(InvalidUrlException.class);
        verifyNoInteractions(client);
    }

    @Test
    void unknownTicketsDoNotSendRequests() {
        assertThat(service.poll(UUID.randomUUID()).status()).isEqualTo(Status.INCONCLUSIVO);
        verifyNoInteractions(client);
    }

    @Test
    void simultaneousSubmissionsShareTheSamePendingAnalysis() throws Exception {
        when(client.getReport(URL)).thenReturn(Optional.empty());
        when(client.submit(URL)).thenReturn("u-one");
        var calls = new ArrayList<Callable<ChatResponse>>();
        for (int i = 0; i < 8; i++) calls.add(() -> service.analyze(URL.toString()));
        try (var executor = Executors.newFixedThreadPool(4)) {
            var results = executor.invokeAll(calls);
            UUID ticket = results.getFirst().get().analysisId();
            for (var result : results) assertThat(result.get().analysisId()).isEqualTo(ticket);
        }
        verify(client, times(1)).getReport(URL);
        verify(client, times(1)).submit(URL);
    }

    @Test
    void fullCacheStillReusesTheSameResult() {
        var normal = TestSupport.properties("test-key");
        var limited = new VirusTotalProperties(normal.apiKey(), normal.timeout(), normal.requestsPerMinute(),
                normal.requestsPerDay(), normal.cacheTtl(), normal.reportMaxAge(), normal.pollInterval(),
                normal.maxPolls(), normal.analysisTimeout(), 1);
        service = new LinkAnalysisService(new UrlValidationService(), client,
                new LinkClassificationService(clock), limited, clock);
        when(client.getReport(URL)).thenReturn(Optional.of(cleanReport()));
        ChatResponse first = service.analyze(URL.toString());
        assertThat(service.analyze(URL.toString())).isEqualTo(first);
        verify(client, times(1)).getReport(URL);
    }
}
