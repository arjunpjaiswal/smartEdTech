package com.smartedtech.attendance.dto;

import com.smartedtech.attendance.Attendance;

import java.time.Instant;

public record AttendanceDto(
        Long id,
        Long sessionId,
        Long userId,
        String userName,
        String userEmail,
        Instant firstJoinedAt,
        Instant lastSeenAt,
        long durationSeconds
) {
    public static AttendanceDto from(Attendance attendance) {
        return new AttendanceDto(
                attendance.getId(),
                attendance.getSession().getId(),
                attendance.getUser().getId(),
                attendance.getUser().getFullName(),
                attendance.getUser().getEmail(),
                attendance.getFirstJoinedAt(),
                attendance.getLastSeenAt(),
                attendance.getDurationSeconds()
        );
    }
}
