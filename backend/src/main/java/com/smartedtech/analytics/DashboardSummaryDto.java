package com.smartedtech.analytics;

import com.smartedtech.classroom.dto.ClassroomDto;
import com.smartedtech.material.dto.MaterialDto;
import com.smartedtech.session.dto.SessionDto;
import com.smartedtech.user.Role;

import java.util.List;

public record DashboardSummaryDto(
        Role role,
        long stat1,
        String stat1Label,
        long stat2,
        String stat2Label,
        long stat3,
        String stat3Label,
        long stat4,
        String stat4Label,
        List<ClassroomDto> classrooms,
        List<SessionDto> upcomingSessions,
        List<MaterialDto> recentMaterials
) {
}
