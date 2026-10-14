package com.smartedtech.user;

import com.smartedtech.security.UserPrincipal;
import com.smartedtech.user.dto.PlatformStatsDto;
import com.smartedtech.user.dto.UpdateUserStatusRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserDto>> listUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<UserDto> users = adminService.searchUsers(role, search, pageable);
        return ResponseEntity.ok(users);
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<UserDto> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserDto user = adminService.updateUserStatus(id, request.enabled(), principal);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/stats")
    public ResponseEntity<PlatformStatsDto> getPlatformStats() {
        PlatformStatsDto stats = adminService.getPlatformStats();
        return ResponseEntity.ok(stats);
    }
}
