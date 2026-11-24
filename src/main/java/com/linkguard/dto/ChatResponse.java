package com.linkguard.dto;

public record ChatResponse(Status status, String reply) {

    public enum Status {
        CONFIÁVEL, SUSPEITO, PERIGOSO, INVALIDO
    }

    public static ChatResponse invalid(String reply) {
        return new ChatResponse(Status.INVALIDO, reply);
    }
}
