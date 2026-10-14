package com.smartedtech.classroom.dto;

public record EnrollStudentRequest(
        String email,
        Long studentId
) {
}
