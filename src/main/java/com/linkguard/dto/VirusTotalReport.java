package com.linkguard.dto;

import java.time.Instant;

public record VirusTotalReport(ScanStats stats, Instant analyzedAt) {
}
