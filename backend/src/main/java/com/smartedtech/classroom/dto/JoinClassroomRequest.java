package com.smartedtech.classroom.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinClassroomRequest(
        @NotBlank(message = "Join code is required")
        String joinCode
) {
}
