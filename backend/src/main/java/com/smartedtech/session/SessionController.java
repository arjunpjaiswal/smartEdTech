package com.smartedtech.session;

import com.smartedtech.attendance.dto.AttendanceDto;
import com.smartedtech.security.UserPrincipal;
import com.smartedtech.session.dto.CreateSessionRequest;
import com.smartedtech.session.dto.JoinSessionResponse;
import com.smartedtech.session.dto.SessionDto;
import com.smartedtech.session.dto.UpdateSessionRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/classrooms/{classroomId}/sessions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<SessionDto> createSession(
            @PathVariable Long classroomId,
            @Valid @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        SessionDto session = sessionService.createSession(classroomId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping("/classrooms/{classroomId}/sessions")
    public ResponseEntity<Page<SessionDto>> getClassroomSessions(
            @PathVariable Long classroomId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<SessionDto> sessions = sessionService.getClassroomSessions(classroomId, principal, pageable);
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<SessionDto> getSession(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        SessionDto session = sessionService.getSession(id, principal);
        return ResponseEntity.ok(session);
    }

    @PutMapping("/sessions/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<SessionDto> updateSession(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSessionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        SessionDto session = sessionService.updateSession(id, request, principal);
        return ResponseEntity.ok(session);
    }

    @PatchMapping("/sessions/{id}/cancel")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<SessionDto> cancelSession(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        SessionDto session = sessionService.cancelSession(id, principal);
        return ResponseEntity.ok(session);
    }

    @PatchMapping("/sessions/{id}/end")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<SessionDto> endSession(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        SessionDto session = sessionService.endSession(id, principal);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/sessions/upcoming")
    public ResponseEntity<List<SessionDto>> getUpcomingSessions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "10") int limit) {
        List<SessionDto> sessions = sessionService.getUpcomingSessions(principal, PageRequest.of(0, limit));
        return ResponseEntity.ok(sessions);
    }

    @PostMapping("/sessions/{id}/join")
    public ResponseEntity<JoinSessionResponse> joinSession(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        JoinSessionResponse response = sessionService.joinSession(id, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions/{id}/attendance/heartbeat")
    public ResponseEntity<Void> recordHeartbeat(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        sessionService.recordHeartbeat(id, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{id}/leave")
    public ResponseEntity<Void> recordLeave(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        sessionService.recordLeave(id, principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions/{id}/attendance")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<List<AttendanceDto>> getSessionAttendance(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<AttendanceDto> attendance = sessionService.getSessionAttendance(id, principal);
        return ResponseEntity.ok(attendance);
    }
}
