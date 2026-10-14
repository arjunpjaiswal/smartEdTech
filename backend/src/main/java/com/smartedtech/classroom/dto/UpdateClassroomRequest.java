package com.smartedtech.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateClassroomRequest(
        @NotBlank(message = "Classroom name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        String description
) {
}
