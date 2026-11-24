package com.linkguard.client;

import com.linkguard.config.VirusTotalProperties;
import com.linkguard.exception.VirusTotalException;
import com.linkguard.exception.VirusTotalException.Reason;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.stereotype.Component;

@Component
public class VirusTotalRateLimiter {

    private final VirusTotalProperties properties;
    private final Clock clock;
    private final Deque<Instant> minuteRequests = new ArrayDeque<>();
    private LocalDate quotaDay;
    private int dailyRequests;
    private Instant blockedUntil = Instant.MIN;

    public VirusTotalRateLimiter(VirusTotalProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized void acquire() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (!today.equals(quotaDay)) {
            quotaDay = today;
            dailyRequests = 0;
        }
        while (!minuteRequests.isEmpty() && !minuteRequests.peekFirst().isAfter(now.minusSeconds(60))) {
            minuteRequests.removeFirst();
        }
        if (now.isBefore(blockedUntil) || minuteRequests.size() >= properties.requestsPerMinute()
                || dailyRequests >= properties.requestsPerDay()) {
            throw new VirusTotalException(Reason.RATE_LIMIT);
        }
        minuteRequests.addLast(now);
        dailyRequests++;
    }

    public synchronized void blockFor(Duration duration) {
        Instant until = clock.instant().plus(duration);
        if (until.isAfter(blockedUntil)) blockedUntil = until;
    }
}
