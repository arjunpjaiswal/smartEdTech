package com.smartedtech.discussion;

import com.smartedtech.discussion.dto.DiscussionMessageDto;
import com.smartedtech.discussion.dto.SendDiscussionRequest;
import com.smartedtech.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/discussion")
public class DiscussionController {

    private final DiscussionService discussionService;

    public DiscussionController(DiscussionService discussionService) {
        this.discussionService = discussionService;
    }

    @GetMapping
    public ResponseEntity<Page<DiscussionMessageDto>> getMessages(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<DiscussionMessageDto> messages = discussionService.getMessages(sessionId, principal, pageable);
        return ResponseEntity.ok(messages);
    }

    @PostMapping
    public ResponseEntity<DiscussionMessageDto> postMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody SendDiscussionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        DiscussionMessageDto message = discussionService.postMessage(sessionId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }
}
