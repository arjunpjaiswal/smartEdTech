package com.smartedtech.auth;

import com.smartedtech.user.UserDto;

public record AuthResponse(
        String accessToken,
        long expiresInSeconds,
        UserDto user
) {
}
