package com.linkguard.dto;

public record VirusTotalAnalysis(String status, VirusTotalReport report) {

    public boolean completed() {
        return "completed".equals(status);
    }
}
