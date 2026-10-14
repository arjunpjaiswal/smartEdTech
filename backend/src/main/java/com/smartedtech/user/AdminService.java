package com.smartedtech.user;

import com.smartedtech.auth.RefreshTokenRepository;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.common.BadRequestException;
import com.smartedtech.common.ResourceNotFoundException;
import com.smartedtech.material.StudyMaterialRepository;
import com.smartedtech.security.UserPrincipal;
import com.smartedtech.session.LiveSessionRepository;
import com.smartedtech.user.dto.PlatformStatsDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomEnrollmentRepository enrollmentRepository;
    private final LiveSessionRepository sessionRepository;
    private final StudyMaterialRepository materialRepository;

    public AdminService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            ClassroomRepository classroomRepository,
            ClassroomEnrollmentRepository enrollmentRepository,
            LiveSessionRepository sessionRepository,
            StudyMaterialRepository materialRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.classroomRepository = classroomRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionRepository = sessionRepository;
        this.materialRepository = materialRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserDto> searchUsers(Role role, String search, Pageable pageable) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        return userRepository.searchUsers(role, cleanSearch, pageable)
                .map(UserDto::from);
    }

    @Transactional
    public UserDto updateUserStatus(Long userId, boolean enabled, UserPrincipal currentAdmin) {
        if (userId.equals(currentAdmin.getId())) {
            throw new BadRequestException("Cannot change your own account status");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setEnabled(enabled);
        user = userRepository.save(user);

        // If deactivated, revoke all refresh tokens immediately
        if (!enabled) {
            refreshTokenRepository.revokeAllByUserId(userId);
        }

        return UserDto.from(user);
    }

    @Transactional(readOnly = true)
    public PlatformStatsDto getPlatformStats() {
        long totalUsers = userRepository.count();
        long totalStudents = userRepository.countByRole(Role.STUDENT);
        long totalTeachers = userRepository.countByRole(Role.TEACHER);
        long totalAdmins = userRepository.countByRole(Role.ADMIN);

        long totalClassrooms = classroomRepository.count();
        long activeClassrooms = classroomRepository.countByArchivedFalse();

        long totalSessions = sessionRepository.count();
        long totalMaterials = materialRepository.count();
        long totalEnrollments = enrollmentRepository.count();

        return new PlatformStatsDto(
                totalUsers,
                totalStudents,
                totalTeachers,
                totalAdmins,
                totalClassrooms,
                activeClassrooms,
                totalSessions,
                totalMaterials,
                totalEnrollments
        );
    }
}
