package com.smartedtech.ai;

public record AiResponse(
        String content,
        String providerUsed
) {
    public String getContent() {
        return content;
    }

    public String getProvider() {
        return providerUsed;
    }

    public String getProviderUsed() {
        return providerUsed;
    }
}
