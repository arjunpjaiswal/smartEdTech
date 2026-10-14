package com.smartedtech.classroom.dto;

import com.smartedtech.classroom.Classroom;

import java.time.Instant;

public record ClassroomDto(
        Long id,
        String name,
        String description,
        String joinCode,
        Long teacherId,
        String teacherName,
        boolean archived,
        long studentCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ClassroomDto from(Classroom classroom, long studentCount) {
        return new ClassroomDto(
                classroom.getId(),
                classroom.getName(),
                classroom.getDescription(),
                classroom.getJoinCode(),
                classroom.getTeacher().getId(),
                classroom.getTeacher().getFullName(),
                classroom.isArchived(),
                studentCount,
                classroom.getCreatedAt(),
                classroom.getUpdatedAt()
        );
    }
}
