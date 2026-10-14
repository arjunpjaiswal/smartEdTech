package com.smartedtech.analytics;

import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.classroom.dto.ClassroomDto;
import com.smartedtech.material.StudyMaterialRepository;
import com.smartedtech.material.dto.MaterialDto;
import com.smartedtech.security.UserPrincipal;
import com.smartedtech.session.LiveSessionRepository;
import com.smartedtech.session.dto.SessionDto;
import com.smartedtech.user.Role;
import com.smartedtech.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomEnrollmentRepository enrollmentRepository;
    private final LiveSessionRepository sessionRepository;
    private final StudyMaterialRepository materialRepository;

    public DashboardService(
            UserRepository userRepository,
            ClassroomRepository classroomRepository,
            ClassroomEnrollmentRepository enrollmentRepository,
            LiveSessionRepository sessionRepository,
            StudyMaterialRepository materialRepository) {
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionRepository = sessionRepository;
        this.materialRepository = materialRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary(UserPrincipal principal) {
        Instant now = Instant.now();
        Role role = principal.getRole();

        if (role == Role.TEACHER) {
            long classroomCount = classroomRepository.countByTeacherId(principal.getId());
            long studentCount = enrollmentRepository.countStudentsByTeacherId(principal.getId());
            long upcomingCount = sessionRepository.countUpcomingForTeacher(principal.getId(), now);
            long materialCount = materialRepository.countByTeacherId(principal.getId());

            List<ClassroomDto> recentClassrooms = classroomRepository.findTop5ByTeacherIdOrderByCreatedAtDesc(principal.getId())
                    .stream()
                    .map(c -> ClassroomDto.from(c, enrollmentRepository.countByClassroomId(c.getId())))
                    .toList();

            List<SessionDto> upcomingSessions = sessionRepository.findUpcomingForTeacher(principal.getId(), now, PageRequest.of(0, 5))
                    .stream()
                    .map(SessionDto::from)
                    .toList();

            return new DashboardSummaryDto(
                    Role.TEACHER,
                    classroomCount, "Classrooms",
                    studentCount, "Enrolled Students",
                    upcomingCount, "Upcoming Sessions",
                    materialCount, "Study Materials",
                    recentClassrooms,
                    upcomingSessions,
                    Collections.emptyList()
            );
        } else if (role == Role.STUDENT) {
            long classroomCount = enrollmentRepository.countByStudentId(principal.getId());
            long upcomingCount = sessionRepository.countUpcomingForStudent(principal.getId(), now);
            long materialCount = materialRepository.countByStudentId(principal.getId());

            List<ClassroomDto> enrolledClassrooms = classroomRepository.findTop5EnrolledByStudentId(principal.getId())
                    .stream()
                    .map(c -> ClassroomDto.from(c, enrollmentRepository.countByClassroomId(c.getId())))
                    .toList();

            List<SessionDto> upcomingSessions = sessionRepository.findUpcomingForStudent(principal.getId(), now, PageRequest.of(0, 5))
                    .stream()
                    .map(SessionDto::from)
                    .toList();

            List<MaterialDto> recentMaterials = materialRepository.findTop5RecentForStudent(principal.getId(), PageRequest.of(0, 5))
                    .stream()
                    .map(MaterialDto::from)
                    .toList();

            return new DashboardSummaryDto(
                    Role.STUDENT,
                    classroomCount, "Enrolled Classes",
                    upcomingCount, "Upcoming Sessions",
                    materialCount, "Accessible Materials",
                    0, "Completed Quizzes",
                    enrolledClassrooms,
                    upcomingSessions,
                    recentMaterials
            );
        } else {
            // ADMIN
            long totalUsers = userRepository.count();
            long totalClassrooms = classroomRepository.count();
            long totalSessions = sessionRepository.count();
            long totalMaterials = materialRepository.count();

            List<SessionDto> upcomingSessions = sessionRepository.findUpcomingAll(now, PageRequest.of(0, 5))
                    .stream()
                    .map(SessionDto::from)
                    .toList();

            List<ClassroomDto> recentClassrooms = classroomRepository.findAll(PageRequest.of(0, 5))
                    .stream()
                    .map(c -> ClassroomDto.from(c, enrollmentRepository.countByClassroomId(c.getId())))
                    .toList();

            return new DashboardSummaryDto(
                    Role.ADMIN,
                    totalUsers, "Total Users",
                    totalClassrooms, "Total Classrooms",
                    totalSessions, "Total Sessions",
                    totalMaterials, "Study Materials",
                    recentClassrooms,
                    upcomingSessions,
                    Collections.emptyList()
            );
        }
    }
}
