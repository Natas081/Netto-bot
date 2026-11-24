package com.linkguard;

import com.linkguard.config.VirusTotalProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class TestSupport {

    private TestSupport() {
    }

    public static VirusTotalProperties properties(String key) {
        return new VirusTotalProperties(key, Duration.ofSeconds(10), 4, 500,
                Duration.ofMinutes(15), Duration.ofHours(24), Duration.ofSeconds(20),
                6, Duration.ofMinutes(5), 200);
    }

    public static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-24T12:00:00Z");

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }
    }
}
