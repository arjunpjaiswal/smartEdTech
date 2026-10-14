package com.smartedtech.session.dto;

public record JoinSessionResponse(
        String token,
        String roomName,
        String domain,
        String appId
) {
}
