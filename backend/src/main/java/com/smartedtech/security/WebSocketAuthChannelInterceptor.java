package com.smartedtech.security;

import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.common.RateLimitExceededException;
import com.smartedtech.session.LiveSession;
import com.smartedtech.session.LiveSessionRepository;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Transactional(readOnly = true)
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern SESSION_TOPIC_PATTERN = Pattern.compile("^/topic/sessions/(\\d+)(?:/(discussion|polls(?:/teacher)?))?$");
    private static final Pattern APP_SESSION_PATTERN = Pattern.compile("^/app/sessions/(\\d+)(?:/(.*))?$");

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final LiveSessionRepository sessionRepository;
    private final ClassroomEnrollmentRepository enrollmentRepository;
    private final RateLimitService rateLimitService;

    public WebSocketAuthChannelInterceptor(
            JwtService jwtService,
            UserRepository userRepository,
            LiveSessionRepository sessionRepository,
            ClassroomEnrollmentRepository enrollmentRepository,
            RateLimitService rateLimitService) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            handleSubscribe(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            handleSend(accessor);
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            authHeader = accessor.getFirstNativeHeader("X-Authorization");
        }
        if (authHeader == null || authHeader.isBlank()) {
            authHeader = accessor.getFirstNativeHeader("token");
        }

        if (authHeader == null || authHeader.isBlank()) {
            throw new AccessDeniedException("Missing Authorization header in STOMP connect");
        }

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!jwtService.isTokenValid(token)) {
            throw new AccessDeniedException("Invalid or expired JWT token in STOMP connect");
        }

        String email = jwtService.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("User not found for STOMP connect"));

        if (!user.isEnabled()) {
            throw new AccessDeniedException("User account is deactivated");
        }

        UserPrincipal principal = UserPrincipal.from(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        if (accessor.isMutable()) {
            accessor.setUser(authentication);
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        Principal rawPrincipal = accessor.getUser();
        UserPrincipal principal = extractPrincipal(rawPrincipal);

        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new AccessDeniedException("Subscription destination cannot be empty");
        }

        if (destination.startsWith("/user/")) {
            return;
        }

        Matcher matcher = SESSION_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            throw new AccessDeniedException("Unauthorized subscription destination: " + destination);
        }

        Long sessionId = Long.parseLong(matcher.group(1));
        String subTopic = matcher.group(2);

        LiveSession session = sessionRepository.findByIdWithClassroomAndTeacher(sessionId)
                .orElseThrow(() -> new AccessDeniedException("Session not found: " + sessionId));

        Classroom classroom = session.getClassroom();

        if (principal.getRole() == Role.ADMIN) {
            return;
        }

        boolean isClassroomTeacher = principal.getRole() == Role.TEACHER &&
                classroom.getTeacher().getId().equals(principal.getId());

        if ("polls/teacher".equals(subTopic)) {
            if (!isClassroomTeacher) {
                throw new AccessDeniedException("Only the classroom teacher can subscribe to teacher poll updates");
            }
            return;
        }

        if (isClassroomTeacher) {
            return;
        }

        boolean isEnrolledStudent = principal.getRole() == Role.STUDENT &&
                enrollmentRepository.existsByClassroomIdAndStudentId(classroom.getId(), principal.getId());

        if (!isEnrolledStudent) {
            throw new AccessDeniedException("Student is not enrolled in the classroom for session " + sessionId);
        }
    }

    private void handleSend(StompHeaderAccessor accessor) {
        Principal rawPrincipal = accessor.getUser();
        UserPrincipal principal = extractPrincipal(rawPrincipal);

        if (!rateLimitService.tryConsumeMessage(principal.getId())) {
            throw new RateLimitExceededException("Message rate limit exceeded. Please wait.");
        }

        String destination = accessor.getDestination();
        if (destination != null) {
            Matcher matcher = APP_SESSION_PATTERN.matcher(destination);
            if (matcher.matches()) {
                Long sessionId = Long.parseLong(matcher.group(1));
                LiveSession session = sessionRepository.findByIdWithClassroomAndTeacher(sessionId)
                        .orElseThrow(() -> new AccessDeniedException("Session not found: " + sessionId));

                Classroom classroom = session.getClassroom();

                if (principal.getRole() == Role.ADMIN) {
                    return;
                }

                boolean isTeacher = principal.getRole() == Role.TEACHER &&
                        classroom.getTeacher().getId().equals(principal.getId());
                boolean isEnrolled = principal.getRole() == Role.STUDENT &&
                        enrollmentRepository.existsByClassroomIdAndStudentId(classroom.getId(), principal.getId());

                if (!isTeacher && !isEnrolled) {
                    throw new AccessDeniedException("User has no access to session " + sessionId);
                }
            }
        }
    }

    private UserPrincipal extractPrincipal(Principal rawPrincipal) {
        if (rawPrincipal instanceof Authentication auth && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }
        throw new AccessDeniedException("Unauthenticated STOMP action");
    }
}
