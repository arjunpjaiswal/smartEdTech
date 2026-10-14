package com.smartedtech.material.dto;

import com.smartedtech.material.StudyMaterial;

import java.time.Instant;

public record MaterialDto(
        Long id,
        Long classroomId,
        String classroomName,
        Long uploaderId,
        String uploaderName,
        String title,
        String description,
        String originalFilename,
        long fileSize,
        String contentType,
        Instant createdAt
) {
    public static MaterialDto from(StudyMaterial material) {
        return new MaterialDto(
                material.getId(),
                material.getClassroom().getId(),
                material.getClassroom().getName(),
                material.getUploader().getId(),
                material.getUploader().getFullName(),
                material.getTitle(),
                material.getDescription(),
                material.getOriginalFilename(),
                material.getFileSize(),
                material.getContentType(),
                material.getCreatedAt()
        );
    }
}
