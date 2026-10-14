package com.smartedtech.user.dto;

public record PlatformStatsDto(
        long totalUsers,
        long totalStudents,
        long totalTeachers,
        long totalAdmins,
        long totalClassrooms,
        long activeClassrooms,
        long totalSessions,
        long totalMaterials,
        long totalEnrollments
) {
}
