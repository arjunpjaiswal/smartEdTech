package com.smartedtech.poll;

import com.smartedtech.poll.dto.CastVoteRequest;
import com.smartedtech.poll.dto.CreatePollRequest;
import com.smartedtech.poll.dto.PollDto;
import com.smartedtech.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/polls")
public class PollController {

    private final PollService pollService;

    public PollController(PollService pollService) {
        this.pollService = pollService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<PollDto> createPoll(
            @PathVariable Long sessionId,
            @Valid @RequestBody CreatePollRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        PollDto poll = pollService.createPoll(sessionId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(poll);
    }

    @GetMapping
    public ResponseEntity<List<PollDto>> getPolls(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<PollDto> polls = pollService.getPolls(sessionId, principal);
        return ResponseEntity.ok(polls);
    }

    @PatchMapping("/{pollId}/open")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<PollDto> openPoll(
            @PathVariable Long sessionId,
            @PathVariable Long pollId,
            @AuthenticationPrincipal UserPrincipal principal) {
        PollDto poll = pollService.openPoll(sessionId, pollId, principal);
        return ResponseEntity.ok(poll);
    }

    @PatchMapping("/{pollId}/close")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<PollDto> closePoll(
            @PathVariable Long sessionId,
            @PathVariable Long pollId,
            @AuthenticationPrincipal UserPrincipal principal) {
        PollDto poll = pollService.closePoll(sessionId, pollId, principal);
        return ResponseEntity.ok(poll);
    }

    @PatchMapping("/{pollId}/visibility")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<PollDto> setResultsVisibility(
            @PathVariable Long sessionId,
            @PathVariable Long pollId,
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean visible = body.getOrDefault("visible", false);
        PollDto poll = pollService.setResultsVisibility(sessionId, pollId, visible, principal);
        return ResponseEntity.ok(poll);
    }

    @PostMapping("/{pollId}/vote")
    public ResponseEntity<PollDto> vote(
            @PathVariable Long sessionId,
            @PathVariable Long pollId,
            @Valid @RequestBody CastVoteRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        PollDto poll = pollService.vote(sessionId, pollId, request, principal);
        return ResponseEntity.ok(poll);
    }
}
