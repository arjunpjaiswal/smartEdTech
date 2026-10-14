package com.smartedtech.discussion;

import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.common.BadRequestException;
import com.smartedtech.common.ForbiddenException;
import com.smartedtech.common.RateLimitExceededException;
import com.smartedtech.common.ResourceNotFoundException;
import com.smartedtech.discussion.dto.DiscussionMessageDto;
import com.smartedtech.discussion.dto.SendDiscussionRequest;
import com.smartedtech.security.RateLimitService;
import com.smartedtech.security.UserPrincipal;
import com.smartedtech.session.LiveSession;
import com.smartedtech.session.LiveSessionRepository;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscussionService {

    private final DiscussionMessageRepository discussionMessageRepository;
    private final LiveSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ClassroomEnrollmentRepository enrollmentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RateLimitService rateLimitService;

    public DiscussionService(
            DiscussionMessageRepository discussionMessageRepository,
            LiveSessionRepository sessionRepository,
            UserRepository userRepository,
            ClassroomEnrollmentRepository enrollmentRepository,
            SimpMessagingTemplate messagingTemplate,
            RateLimitService rateLimitService) {
        this.discussionMessageRepository = discussionMessageRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.messagingTemplate = messagingTemplate;
        this.rateLimitService = rateLimitService;
    }

    @Transactional(readOnly = true)
    public Page<DiscussionMessageDto> getMessages(Long sessionId, UserPrincipal principal, Pageable pageable) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        checkMemberOrAdminAccess(session.getClassroom(), principal);

        return discussionMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId, pageable)
                .map(DiscussionMessageDto::from);
    }

    @Transactional
    public DiscussionMessageDto postMessage(Long sessionId, SendDiscussionRequest request, UserPrincipal principal) {
        if (!rateLimitService.tryConsumeMessage(principal.getId())) {
            throw new RateLimitExceededException("Message rate limit exceeded. Please wait a moment before sending again.");
        }

        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        checkMemberOrAdminAccess(session.getClassroom(), principal);

        String content = request.getContent() != null ? request.getContent().trim() : "";
        if (content.isEmpty() || content.length() > 1000) {
            throw new BadRequestException("Message content must be between 1 and 1000 characters");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DiscussionMessage message = new DiscussionMessage(session, user, content);
        DiscussionMessage saved = discussionMessageRepository.save(message);

        DiscussionMessageDto dto = DiscussionMessageDto.from(saved);

        // Broadcast to STOMP topic
        messagingTemplate.convertAndSend("/topic/sessions/" + sessionId + "/discussion", dto);

        return dto;
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
        throw new ForbiddenException("You are not authorized to access discussion for this session");
    }
}
