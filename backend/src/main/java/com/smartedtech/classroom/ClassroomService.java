package com.smartedtech.classroom;

import com.smartedtech.classroom.dto.*;
import com.smartedtech.common.BadRequestException;
import com.smartedtech.common.ConflictException;
import com.smartedtech.common.ForbiddenException;
import com.smartedtech.common.ResourceNotFoundException;
import com.smartedtech.notification.NotificationService;
import com.smartedtech.security.UserPrincipal;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;

@Service
public class ClassroomService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private final SecureRandom secureRandom = new SecureRandom();

    private final ClassroomRepository classroomRepository;
    private final ClassroomEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ClassroomService(
            ClassroomRepository classroomRepository,
            ClassroomEnrollmentRepository enrollmentRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this.classroomRepository = classroomRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public ClassroomDto createClassroom(CreateClassroomRequest request, UserPrincipal principal) {
        User teacher = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (teacher.getRole() != Role.TEACHER && teacher.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only teachers or admins can create classrooms");
        }

        String joinCode = generateUniqueJoinCode();
        Classroom classroom = new Classroom(
                request.name().trim(),
                request.description() != null ? request.description().trim() : null,
                joinCode,
                teacher
        );

        classroom = classroomRepository.save(classroom);
        return ClassroomDto.from(classroom, 0);
    }

    @Transactional
    public ClassroomDto updateClassroom(Long id, UpdateClassroomRequest request, UserPrincipal principal) {
        Classroom classroom = classroomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));

        checkTeacherOrAdminAccess(classroom, principal);

        classroom.setName(request.name().trim());
        classroom.setDescription(request.description() != null ? request.description().trim() : null);

        classroom = classroomRepository.save(classroom);
        long studentCount = enrollmentRepository.countByClassroomId(classroom.getId());
        return ClassroomDto.from(classroom, studentCount);
    }

    @Transactional
    public ClassroomDto setArchiveStatus(Long id, boolean archived, UserPrincipal principal) {
        Classroom classroom = classroomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));

        checkTeacherOrAdminAccess(classroom, principal);

        classroom.setArchived(archived);
        classroom = classroomRepository.save(classroom);
        long studentCount = enrollmentRepository.countByClassroomId(classroom.getId());
        return ClassroomDto.from(classroom, studentCount);
    }

    @Transactional(readOnly = true)
    public Page<ClassroomDto> getClassrooms(UserPrincipal principal, Pageable pageable) {
        Page<Classroom> page;
        if (principal.getRole() == Role.ADMIN) {
            page = classroomRepository.findAll(pageable);
        } else if (principal.getRole() == Role.TEACHER) {
            page = classroomRepository.findByTeacherId(principal.getId(), pageable);
        } else {
            page = classroomRepository.findEnrolledByStudentId(principal.getId(), pageable);
        }

        return page.map(c -> ClassroomDto.from(c, enrollmentRepository.countByClassroomId(c.getId())));
    }

    @Transactional(readOnly = true)
    public ClassroomDto getClassroomDetails(Long id, UserPrincipal principal) {
        Classroom classroom = classroomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));

        checkMemberOrAdminAccess(classroom, principal);

        long studentCount = enrollmentRepository.countByClassroomId(classroom.getId());
        return ClassroomDto.from(classroom, studentCount);
    }

    @Transactional
    public ClassroomDto joinByCode(JoinClassroomRequest request, UserPrincipal principal) {
        if (principal.getRole() != Role.STUDENT) {
            throw new ForbiddenException("Only students can join classrooms via join code");
        }

        String code = request.joinCode().trim().toUpperCase(Locale.ROOT);
        Classroom classroom = classroomRepository.findByJoinCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found with join code: " + code));

        if (classroom.isArchived()) {
            throw new BadRequestException("Cannot join an archived classroom");
        }

        if (enrollmentRepository.existsByClassroomIdAndStudentId(classroom.getId(), principal.getId())) {
            throw new ConflictException("Already enrolled in this classroom");
        }

        User student = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ClassroomEnrollment enrollment = new ClassroomEnrollment(classroom, student);
        enrollmentRepository.save(enrollment);

        notificationService.createNotification(
                student,
                "ENROLLMENT",
                "Joined Classroom",
                "You have successfully enrolled in " + classroom.getName()
        );

        notificationService.createNotification(
                classroom.getTeacher(),
                "ENROLLMENT",
                "New Student Enrolled",
                student.getFullName() + " enrolled in " + classroom.getName()
        );

        long studentCount = enrollmentRepository.countByClassroomId(classroom.getId());
        return ClassroomDto.from(classroom, studentCount);
    }

    @Transactional
    public EnrollmentDto enrollStudent(Long classroomId, EnrollStudentRequest request, UserPrincipal principal) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));

        checkTeacherOrAdminAccess(classroom, principal);

        User student;
        if (request.studentId() != null) {
            student = userRepository.findById(request.studentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + request.studentId()));
        } else if (request.email() != null && !request.email().isBlank()) {
            student = userRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found with email: " + request.email()));
        } else {
            throw new BadRequestException("Either studentId or email must be provided");
        }

        if (student.getRole() != Role.STUDENT) {
            throw new BadRequestException("Can only enroll users with STUDENT role");
        }

        if (enrollmentRepository.existsByClassroomIdAndStudentId(classroom.getId(), student.getId())) {
            throw new ConflictException("Student is already enrolled in this classroom");
        }

        ClassroomEnrollment enrollment = new ClassroomEnrollment(classroom, student);
        enrollment = enrollmentRepository.save(enrollment);

        notificationService.createNotification(
                student,
                "ENROLLMENT",
                "Enrolled in Classroom",
                "You have been added to " + classroom.getName() + " by " + classroom.getTeacher().getFullName()
        );

        return EnrollmentDto.from(enrollment);
    }

    @Transactional
    public void removeStudent(Long classroomId, Long studentId, UserPrincipal principal) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));

        checkTeacherOrAdminAccess(classroom, principal);

        if (!enrollmentRepository.existsByClassroomIdAndStudentId(classroomId, studentId)) {
            throw new ResourceNotFoundException("Student is not enrolled in this classroom");
        }

        enrollmentRepository.deleteByClassroomIdAndStudentId(classroomId, studentId);
    }

    @Transactional(readOnly = true)
    public Page<EnrollmentDto> getEnrolledStudents(Long classroomId, UserPrincipal principal, Pageable pageable) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));

        checkMemberOrAdminAccess(classroom, principal);

        return enrollmentRepository.findByClassroomId(classroomId, pageable)
                .map(EnrollmentDto::from);
    }

    public void checkTeacherOrAdminAccess(Classroom classroom, UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (principal.getRole() != Role.TEACHER || !classroom.getTeacher().getId().equals(principal.getId())) {
            throw new ForbiddenException("You do not have permission to manage this classroom");
        }
    }

    public void checkMemberOrAdminAccess(Classroom classroom, UserPrincipal principal) {
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

    private String generateUniqueJoinCode() {
        for (int i = 0; i < 100; i++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!classroomRepository.existsByJoinCode(code)) {
                return code;
            }
        }
        throw new RuntimeException("Failed to generate a unique classroom join code");
    }
}
