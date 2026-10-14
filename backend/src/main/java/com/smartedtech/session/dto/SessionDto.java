package com.smartedtech.session.dto;

import com.smartedtech.session.LiveSession;

import java.time.Instant;

public record SessionDto(
        Long id,
        Long classroomId,
        String classroomName,
        String title,
        String description,
        Instant scheduledStartTime,
        Instant scheduledEndTime,
        String status,
        String roomName,
        Instant createdAt
) {
    public static SessionDto from(LiveSession session) {
        return new SessionDto(
                session.getId(),
                session.getClassroom().getId(),
                session.getClassroom().getName(),
                session.getTitle(),
                session.getDescription(),
                session.getScheduledStartTime(),
                session.getScheduledEndTime(),
                session.getStatus(),
                session.getRoomName(),
                session.getCreatedAt()
        );
    }
}
