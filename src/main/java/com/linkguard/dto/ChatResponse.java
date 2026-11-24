package com.linkguard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(Status status, String reply, String domain, ScanStats stats,
                           Instant analyzedAt, String source, UUID analysisId, Integer retryAfterSeconds) {

    public enum Status {
        CONFIÁVEL, SUSPEITO, PERIGOSO, EM_ANALISE, INCONCLUSIVO
    }

    public static ChatResponse inconclusive(String domain, String reply) {
        return new ChatResponse(Status.INCONCLUSIVO, reply, domain, null, null, null, null, null);
    }
}
