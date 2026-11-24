package com.linkguard.client;

import com.linkguard.TestSupport;
import com.linkguard.TestSupport.MutableClock;
import com.linkguard.config.VirusTotalProperties;
import com.linkguard.exception.VirusTotalException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VirusTotalRateLimiterTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void enforcesARollingMinuteAndAllowsTheNextWindow() {
        var limiter = new VirusTotalRateLimiter(TestSupport.properties("test-key"), clock);
        for (int i = 0; i < 4; i++) limiter.acquire();
        assertThatThrownBy(limiter::acquire).isInstanceOf(VirusTotalException.class);
        clock.advance(Duration.ofSeconds(59));
        assertThatThrownBy(limiter::acquire).isInstanceOf(VirusTotalException.class);
        clock.advance(Duration.ofSeconds(1));
        assertThatCode(limiter::acquire).doesNotThrowAnyException();
    }

    @Test
    void dailyQuotaResetsAtUtcMidnight() {
        VirusTotalProperties normal = TestSupport.properties("test-key");
        var limited = new VirusTotalProperties(normal.apiKey(), normal.timeout(), 4, 2, normal.cacheTtl(),
                normal.reportMaxAge(), normal.pollInterval(), normal.maxPolls(), normal.analysisTimeout(), normal.cacheMaxEntries());
        var limiter = new VirusTotalRateLimiter(limited, clock);
        limiter.acquire();
        limiter.acquire();
        clock.advance(Duration.ofMinutes(2));
        assertThatThrownBy(limiter::acquire).isInstanceOf(VirusTotalException.class);
        clock.advance(Duration.ofHours(12));
        assertThatCode(limiter::acquire).doesNotThrowAnyException();
    }
}
