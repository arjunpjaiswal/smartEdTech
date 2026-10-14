package com.smartedtech.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateSessionRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        String description,

        @NotNull(message = "Scheduled start time is required")
        Instant scheduledStartTime,

        @NotNull(message = "Scheduled end time is required")
        Instant scheduledEndTime
) {
}
