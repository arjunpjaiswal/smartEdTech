package com.smartedtech.discussion;

import com.smartedtech.discussion.dto.SendDiscussionRequest;
import com.smartedtech.security.UserPrincipal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class DiscussionStompController {

    private final DiscussionService discussionService;

    public DiscussionStompController(DiscussionService discussionService) {
        this.discussionService = discussionService;
    }

    @MessageMapping("/sessions/{sessionId}/discussion")
    public void handleMessage(
            @DestinationVariable Long sessionId,
            @Payload SendDiscussionRequest request,
            Principal principal) {
        UserPrincipal userPrincipal = extractPrincipal(principal);
        discussionService.postMessage(sessionId, request, userPrincipal);
    }

    private UserPrincipal extractPrincipal(Principal rawPrincipal) {
        if (rawPrincipal instanceof Authentication auth && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }
        throw new AccessDeniedException("Unauthenticated STOMP action");
    }
}
