package com.smartedtech.ai;

public class AiPermanentException extends RuntimeException {
    private final String provider;
    private final int statusCode;

    public AiPermanentException(String provider, int statusCode, String message) {
        super(String.format("[%s] Permanent error (HTTP %d): %s", provider, statusCode, message));
        this.provider = provider;
        this.statusCode = statusCode;
    }

    public String getProvider() {
        return provider;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
