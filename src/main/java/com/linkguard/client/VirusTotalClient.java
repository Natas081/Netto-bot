package com.linkguard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkguard.config.VirusTotalProperties;
import com.linkguard.dto.ScanStats;
import com.linkguard.dto.VirusTotalAnalysis;
import com.linkguard.dto.VirusTotalReport;
import com.linkguard.exception.VirusTotalException;
import com.linkguard.exception.VirusTotalException.Reason;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class VirusTotalClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(VirusTotalClient.class);
    private final RestClient restClient;
    private final VirusTotalProperties properties;
    private final VirusTotalRateLimiter limiter;
    private final Clock clock;

    public VirusTotalClient(RestClient virusTotalRestClient, VirusTotalProperties properties,
                            VirusTotalRateLimiter limiter, Clock clock) {
        this.restClient = virusTotalRestClient;
        this.properties = properties;
        this.limiter = limiter;
        this.clock = clock;
        if (!properties.isConfigured()) {
            LOGGER.warn("VIRUSTOTAL_API_KEY não configurada. As verificações retornarão INCONCLUSIVO.");
        }
    }

    public Optional<VirusTotalReport> getReport(URI url) {
        prepareRequest();
        String id = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(url.toASCIIString().getBytes(StandardCharsets.UTF_8));
        try {
            JsonNode body = restClient.get().uri("/urls/{id}", id)
                    .header("x-apikey", properties.apiKey().strip()).retrieve().body(JsonNode.class);
            JsonNode attributes = attributes(body, "url");
            return Optional.of(new VirusTotalReport(readStats(attributes.path("last_analysis_stats")),
                    readDate(attributes.path("last_analysis_date"))));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return Optional.empty();
            throw translate(ex);
        } catch (RestClientException ex) {
            throw new VirusTotalException(Reason.UNAVAILABLE);
        }
    }

    public String submit(URI url) {
        prepareRequest();
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("url", url.toASCIIString());
        try {
            JsonNode body = restClient.post().uri("/urls")
                    .header("x-apikey", properties.apiKey().strip())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class);
            if (body == null || !"analysis".equals(body.path("data").path("type").asText())) {
                throw new VirusTotalException(Reason.INVALID_RESPONSE);
            }
            String id = body.path("data").path("id").asText();
            if (id.isBlank() || id.length() > 512 || !id.matches("[A-Za-z0-9_+=-]+")) {
                throw new VirusTotalException(Reason.INVALID_RESPONSE);
            }
            return id;
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (RestClientException ex) {
            throw new VirusTotalException(Reason.UNAVAILABLE);
        }
    }

    public VirusTotalAnalysis getAnalysis(String id) {
        prepareRequest();
        try {
            JsonNode body = restClient.get().uri("/analyses/{id}", id)
                    .header("x-apikey", properties.apiKey().strip()).retrieve().body(JsonNode.class);
            JsonNode attributes = attributes(body, "analysis");
            String status = attributes.path("status").asText();
            if (!"queued".equals(status) && !"in-progress".equals(status) && !"completed".equals(status)) {
                throw new VirusTotalException(Reason.INVALID_RESPONSE);
            }
            VirusTotalReport report = "completed".equals(status)
                    ? new VirusTotalReport(readStats(attributes.path("stats")), readDate(attributes.path("date")))
                    : null;
            return new VirusTotalAnalysis(status, report);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (RestClientException ex) {
            throw new VirusTotalException(Reason.UNAVAILABLE);
        }
    }

    private void prepareRequest() {
        if (!properties.isConfigured()) throw new VirusTotalException(Reason.NOT_CONFIGURED);
        if (!properties.apiKey().strip().chars().allMatch(character -> character >= 33 && character <= 126)) {
            throw new VirusTotalException(Reason.UNAUTHORIZED);
        }
        limiter.acquire();
    }

    private JsonNode attributes(JsonNode body, String expectedType) {
        if (body == null || !expectedType.equals(body.path("data").path("type").asText())
                || !body.path("data").path("attributes").isObject()) {
            throw new VirusTotalException(Reason.INVALID_RESPONSE);
        }
        return body.path("data").path("attributes");
    }

    private ScanStats readStats(JsonNode stats) {
        if (stats.isMissingNode() || stats.isNull() || (stats.isObject() && stats.isEmpty())) return null;
        return new ScanStats(count(stats, "malicious"), count(stats, "suspicious"),
                count(stats, "harmless"), count(stats, "undetected"));
    }

    private int count(JsonNode stats, String name) {
        JsonNode number = stats.path(name);
        if (!number.isIntegralNumber() || !number.canConvertToInt() || number.intValue() < 0) {
            throw new VirusTotalException(Reason.INVALID_RESPONSE);
        }
        return number.intValue();
    }

    private Instant readDate(JsonNode date) {
        if (date.isMissingNode() || date.isNull()) return null;
        if (!date.isIntegralNumber() || !date.canConvertToLong() || date.longValue() <= 0
                || date.longValue() > clock.instant().plusSeconds(300).getEpochSecond()) {
            throw new VirusTotalException(Reason.INVALID_RESPONSE);
        }
        return Instant.ofEpochSecond(date.longValue());
    }

    private VirusTotalException translate(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 401 || status == 403) return new VirusTotalException(Reason.UNAUTHORIZED);
        if (status == 429) {
            limiter.blockFor(retryAfter(ex.getResponseHeaders()));
            return new VirusTotalException(Reason.RATE_LIMIT);
        }
        return new VirusTotalException(Reason.UNAVAILABLE);
    }

    private Duration retryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value != null) {
            try {
                return Duration.ofSeconds(Math.max(60, Math.min(86400, Long.parseLong(value))));
            } catch (NumberFormatException ex) {
                try {
                    Instant until = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                    long seconds = Duration.between(clock.instant(), until).getSeconds();
                    return Duration.ofSeconds(Math.max(60, Math.min(86400, seconds)));
                } catch (DateTimeParseException ignored) {
                    return Duration.ofMinutes(1);
                }
            }
        }
        return Duration.ofMinutes(1);
    }
}
