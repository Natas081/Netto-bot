package com.linkguard.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("virustotal")
public record VirusTotalProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("4") int requestsPerMinute,
        @DefaultValue("500") int requestsPerDay,
        @DefaultValue("15m") Duration cacheTtl,
        @DefaultValue("24h") Duration reportMaxAge,
        @DefaultValue("20s") Duration pollInterval,
        @DefaultValue("6") int maxPolls,
        @DefaultValue("5m") Duration analysisTimeout,
        @DefaultValue("200") int cacheMaxEntries) {

    public VirusTotalProperties {
        if (requestsPerMinute < 1 || requestsPerDay < 1 || maxPolls < 1 || cacheMaxEntries < 1
                || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0
                || cacheTtl.isNegative() || cacheTtl.isZero() || reportMaxAge.isNegative() || reportMaxAge.isZero()
                || pollInterval.compareTo(Duration.ofSeconds(20)) < 0
                || analysisTimeout.isNegative() || analysisTimeout.isZero()) {
            throw new IllegalArgumentException("Revise os limites e os tempos da configuração virustotal.");
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "VirusTotalProperties[apiKey=REDACTED]";
    }
}
