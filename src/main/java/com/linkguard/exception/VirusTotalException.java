package com.linkguard.exception;

public class VirusTotalException extends RuntimeException {

    public enum Reason { NOT_CONFIGURED, UNAUTHORIZED, RATE_LIMIT, UNAVAILABLE, INVALID_RESPONSE }

    private final Reason reason;

    public VirusTotalException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
