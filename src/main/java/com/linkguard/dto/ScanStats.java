package com.linkguard.dto;

public record ScanStats(int malicious, int suspicious, int harmless, int undetected) {

    public ScanStats {
        if (malicious < 0 || suspicious < 0 || harmless < 0 || undetected < 0) {
            throw new IllegalArgumentException("Os totais de mecanismos devem ser positivos ou zero.");
        }
    }

    public boolean hasVerdicts() {
        return malicious > 0 || suspicious > 0 || harmless > 0;
    }
}
