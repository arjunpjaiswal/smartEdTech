package com.smartedtech.session;

import com.smartedtech.attendance.Attendance;
import com.smartedtech.attendance.AttendanceRepository;
import com.smartedtech.attendance.dto.AttendanceDto;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.common.BadRequestException;
import com.smartedtech.common.ForbiddenException;
import com.smartedtech.common.ResourceNotFoundException;
import com.smartedtech.notification.NotificationService;
import com.smartedtech.security.UserPrincipal;
import com.smartedtech.session.dto.CreateSessionRequest;
import com.smartedtech.session.dto.JoinSessionResponse;
import com.smartedtech.session.dto.SessionDto;
import com.smartedtech.session.dto.UpdateSessionRequest;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private final LiveSessionRepository sessionRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomEnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final JitsiService jitsiService;
    private final NotificationService notificationService;

    public SessionService(
            LiveSessionRepository sessionRepository,
            ClassroomRepository classroomRepository,
            ClassroomEnrollmentRepository enrollmentRepository,
            AttendanceRepository attendanceRepository,
            UserRepository userRepository,
            JitsiService jitsiService,
            NotificationService notificationService) {
        this.sessionRepository = sessionRepository;
        this.classroomRepository = classroomRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.jitsiService = jitsiService;
        this.notificationService = notificationService;
    }

    @Transactional
    public SessionDto createSession(Long classroomId, CreateSessionRequest request, UserPrincipal principal) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));

        checkTeacherOrAdminAccess(classroom, principal);

        if (!request.scheduledEndTime().isAfter(request.scheduledStartTime())) {
            throw new BadRequestException("Scheduled end time must be after start time");
        }

        String safeName = classroom.getName().replaceAll("[^a-zA-Z0-9]", "");
        String roomName = safeName + "_" + UUID.randomUUID().toString().substring(0, 8);

        LiveSession session = new LiveSession(
                classroom,
                request.title().trim(),
                request.description() != null ? request.description().trim() : null,
                request.scheduledStartTime(),
                request.scheduledEndTime(),
                roomName
        );

        session = sessionRepository.save(session);

        notificationService.notifyClassroomStudents(
                classroom,
                "SESSION_SCHEDULED",
                "New Live Session: " + session.getTitle(),
                "A new live session has been scheduled in " + classroom.getName() + " on " + session.getScheduledStartTime()
        );

        return SessionDto.from(session);
    }

    @Transactional
    public SessionDto updateSession(Long sessionId, UpdateSessionRequest request, UserPrincipal principal) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        checkTeacherOrAdminAccess(session.getClassroom(), principal);

        if (!request.scheduledEndTime().isAfter(request.scheduledStartTime())) {
            throw new BadRequestException("Scheduled end time must be after start time");
        }

        session.setTitle(request.title().trim());
        session.setDescription(request.description() != null ? request.description().trim() : null);
        session.setScheduledStartTime(request.scheduledStartTime());
        session.setScheduledEndTime(request.scheduledEndTime());

        session = sessionRepository.save(session);
        return SessionDto.from(session);
    }

    @Transactional
    public SessionDto cancelSession(Long sessionId, UserPrincipal principal) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        checkTeacherOrAdminAccess(session.getClassroom(), principal);

        session.setStatus("CANCELLED");
        session = sessionRepository.save(session);
        return SessionDto.from(session);
    }

    @Transactional
    public SessionDto endSession(Long sessionId, UserPrincipal principal) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        checkTeacherOrAdminAccess(session.getClassroom(), principal);

        session.setStatus("ENDED");
        session = sessionRepository.save(session);
        return SessionDto.from(session);
    }

    @Transactional(readOnly = true)
    public SessionDto getSession(Long sessionId, UserPrincipal principal) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        checkMemberOrAdminAccess(session.getClassroom(), principal);

        return SessionDto.from(session);
    }

    @Transactional(readOnly = true)
    public Page<SessionDto> getClassroomSessions(Long classroomId, UserPrincipal principal, Pageable pageable) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));

        checkMemberOrAdminAccess(classroom, principal);

        return sessionRepository.findByClassroomIdOrderByScheduledStartTimeDesc(classroomId, pageable)
                .map(SessionDto::from);
    }

    @Transactional(readOnly = true)
    public List<SessionDto> getUpcomingSessions(UserPrincipal principal, Pageable pageable) {
        Instant now = Instant.now();
        List<LiveSession> sessions;
        if (principal.getRole() == Role.ADMIN) {
            sessions = sessionRepository.findUpcomingAll(now, pageable);
        } else if (principal.getRole() == Role.TEACHER) {
            sessions = sessionRepository.findUpcomingForTeacher(principal.getId(), now, pageable);
        } else {
            sessions = sessionRepository.findUpcomingForStudent(principal.getId(), now, pageable);
        }
        return sessions.stream().map(SessionDto::from).toList();
    }

    @Transactional(noRollbackFor = com.smartedtech.common.IntegrationNotConfiguredException.class)
    public JoinSessionResponse joinSession(Long sessionId, UserPrincipal principal) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        checkMemberOrAdminAccess(session.getClassroom(), principal);

        if ("CANCELLED".equalsIgnoreCase(session.getStatus())) {
            throw new BadRequestException("Cannot join a cancelled session");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isModerator = principal.getRole() == Role.TEACHER || principal.getRole() == Role.ADMIN;

        // If teacher enters scheduled session, activate it
        if (isModerator && "SCHEDULED".equalsIgnoreCase(session.getStatus())) {
            session.setStatus("ACTIVE");
            sessionRepository.save(session);
        }

        // Record attendance
        Instant now = Instant.now();
        Attendance attendance = attendanceRepository.findBySessionIdAndUserId(sessionId, user.getId())
                .orElseGet(() -> new Attendance(session, user, now));
        attendance.setLastSeenAt(now);
        attendanceRepository.saveAndFlush(attendance);

        // Generate Jitsi token (throws 503 IntegrationNotConfiguredException if Jitsi is not configured)
        String fullRoomName = jitsiService.getAppId().isBlank()
                ? session.getRoomName()
                : jitsiService.getAppId() + "/" + session.getRoomName();

        String token = jitsiService.generateToken(user, fullRoomName, isModerator);

        return new JoinSessionResponse(
                token,
                fullRoomName,
                "8x8.vc",
                jitsiService.getAppId()
        );
    }

    @Transactional
    public void recordHeartbeat(Long sessionId, UserPrincipal principal) {
        attendanceRepository.findBySessionIdAndUserId(sessionId, principal.getId())
                .ifPresent(attendance -> {
                    Instant now = Instant.now();
                    long duration = Math.max(0, Duration.between(attendance.getFirstJoinedAt(), now).toSeconds());
                    attendance.setLastSeenAt(now);
                    attendance.setDurationSeconds(duration);
                    attendanceRepository.save(attendance);
                });
    }

    @Transactional
    public void recordLeave(Long sessionId, UserPrincipal principal) {
        attendanceRepository.findBySessionIdAndUserId(sessionId, principal.getId())
                .ifPresent(attendance -> {
                    Instant now = Instant.now();
                    long duration = Math.max(0, Duration.between(attendance.getFirstJoinedAt(), now).toSeconds());
                    attendance.setLastSeenAt(now);
                    attendance.setDurationSeconds(duration);
                    attendanceRepository.save(attendance);
                });
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto> getSessionAttendance(Long sessionId, UserPrincipal principal) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        checkTeacherOrAdminAccess(session.getClassroom(), principal);

        return attendanceRepository.findBySessionIdOrderByFirstJoinedAtAsc(sessionId).stream()
                .map(AttendanceDto::from)
                .toList();
    }

    private void checkTeacherOrAdminAccess(Classroom classroom, UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (principal.getRole() != Role.TEACHER || !classroom.getTeacher().getId().equals(principal.getId())) {
            throw new ForbiddenException("You do not have permission to manage sessions for this classroom");
        }
    }

    private void checkMemberOrAdminAccess(Classroom classroom, UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (principal.getRole() == Role.TEACHER && classroom.getTeacher().getId().equals(principal.getId())) {
            return;
        }
        if (principal.getRole() == Role.STUDENT &&
                enrollmentRepository.existsByClassroomIdAndStudentId(classroom.getId(), principal.getId())) {
            return;
        }
        throw new ForbiddenException("You are not enrolled in or managing this classroom");
    }
}
