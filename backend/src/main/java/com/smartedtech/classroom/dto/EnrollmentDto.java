package com.smartedtech.classroom.dto;

import com.smartedtech.classroom.ClassroomEnrollment;

import java.time.Instant;

public record EnrollmentDto(
        Long id,
        Long studentId,
        String studentName,
        String studentEmail,
        Instant enrolledAt
) {
    public static EnrollmentDto from(ClassroomEnrollment enrollment) {
        return new EnrollmentDto(
                enrollment.getId(),
                enrollment.getStudent().getId(),
                enrollment.getStudent().getFullName(),
                enrollment.getStudent().getEmail(),
                enrollment.getCreatedAt()
        );
    }
}
