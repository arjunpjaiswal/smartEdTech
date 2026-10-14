package com.smartedtech.classroom;

import com.smartedtech.classroom.dto.*;
import com.smartedtech.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/classrooms")
public class ClassroomController {

    private final ClassroomService classroomService;

    public ClassroomController(ClassroomService classroomService) {
        this.classroomService = classroomService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ClassroomDto> createClassroom(
            @Valid @RequestBody CreateClassroomRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ClassroomDto classroom = classroomService.createClassroom(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(classroom);
    }

    @GetMapping
    public ResponseEntity<Page<ClassroomDto>> getClassrooms(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ClassroomDto> classrooms = classroomService.getClassrooms(principal, pageable);
        return ResponseEntity.ok(classrooms);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassroomDto> getClassroomDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ClassroomDto classroom = classroomService.getClassroomDetails(id, principal);
        return ResponseEntity.ok(classroom);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ClassroomDto> updateClassroom(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClassroomRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ClassroomDto classroom = classroomService.updateClassroom(id, request, principal);
        return ResponseEntity.ok(classroom);
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ClassroomDto> setArchiveStatus(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Boolean> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean archived = body != null ? body.getOrDefault("archived", true) : true;
        ClassroomDto classroom = classroomService.setArchiveStatus(id, archived, principal);
        return ResponseEntity.ok(classroom);
    }

    @PostMapping("/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ClassroomDto> joinByCode(
            @Valid @RequestBody JoinClassroomRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ClassroomDto classroom = classroomService.joinByCode(request, principal);
        return ResponseEntity.ok(classroom);
    }

    @PostMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<EnrollmentDto> enrollStudent(
            @PathVariable Long id,
            @Valid @RequestBody EnrollStudentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        EnrollmentDto enrollment = classroomService.enrollStudent(id, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollment);
    }

    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<Void> removeStudent(
            @PathVariable Long id,
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        classroomService.removeStudent(id, studentId, principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/students")
    public ResponseEntity<Page<EnrollmentDto>> getEnrolledStudents(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<EnrollmentDto> students = classroomService.getEnrolledStudents(id, principal, pageable);
        return ResponseEntity.ok(students);
    }
}
