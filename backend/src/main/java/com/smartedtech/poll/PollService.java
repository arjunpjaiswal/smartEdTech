package com.smartedtech.poll;

import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.common.BadRequestException;
import com.smartedtech.common.ConflictException;
import com.smartedtech.common.ForbiddenException;
import com.smartedtech.common.ResourceNotFoundException;
import com.smartedtech.poll.dto.CastVoteRequest;
import com.smartedtech.poll.dto.CreatePollRequest;
import com.smartedtech.poll.dto.PollDto;
import com.smartedtech.poll.dto.PollOptionDto;
import com.smartedtech.security.UserPrincipal;
import com.smartedtech.session.LiveSession;
import com.smartedtech.session.LiveSessionRepository;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PollService {

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final LiveSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ClassroomEnrollmentRepository enrollmentRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public PollService(
            PollRepository pollRepository,
            PollOptionRepository pollOptionRepository,
            PollVoteRepository pollVoteRepository,
            LiveSessionRepository sessionRepository,
            UserRepository userRepository,
            ClassroomEnrollmentRepository enrollmentRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.pollRepository = pollRepository;
        this.pollOptionRepository = pollOptionRepository;
        this.pollVoteRepository = pollVoteRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public PollDto createPoll(Long sessionId, CreatePollRequest request, UserPrincipal principal) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        checkTeacherOrAdminAccess(session.getClassroom(), principal);

        User creator = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Poll poll = new Poll(session, creator, request.getQuestion(), request.isResultsVisibleToStudents());
        List<PollOption> options = new ArrayList<>();
        for (int i = 0; i < request.getOptions().size(); i++) {
            options.add(new PollOption(poll, request.getOptions().get(i), i));
        }
        poll.setOptions(options);

        Poll saved = pollRepository.save(poll);
        PollDto dto = toDto(saved, principal.getId(), true);

        broadcastPollUpdate(session.getId(), saved);

        return dto;
    }

    @Transactional
    public PollDto openPoll(Long sessionId, Long pollId, UserPrincipal principal) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        checkTeacherOrAdminAccess(session.getClassroom(), principal);

        Poll poll = pollRepository.findByIdAndSessionId(pollId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found in this session"));

        poll.setStatus("OPEN");
        Poll saved = pollRepository.save(poll);

        broadcastPollUpdate(sessionId, saved);
        return toDto(saved, principal.getId(), true);
    }

    @Transactional
    public PollDto closePoll(Long sessionId, Long pollId, UserPrincipal principal) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        checkTeacherOrAdminAccess(session.getClassroom(), principal);

        Poll poll = pollRepository.findByIdAndSessionId(pollId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found in this session"));

        poll.setStatus("CLOSED");
        Poll saved = pollRepository.save(poll);

        broadcastPollUpdate(sessionId, saved);
        return toDto(saved, principal.getId(), true);
    }

    @Transactional
    public PollDto setResultsVisibility(Long sessionId, Long pollId, boolean visible, UserPrincipal principal) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        checkTeacherOrAdminAccess(session.getClassroom(), principal);

        Poll poll = pollRepository.findByIdAndSessionId(pollId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found in this session"));

        poll.setResultsVisibleToStudents(visible);
        Poll saved = pollRepository.save(poll);

        broadcastPollUpdate(sessionId, saved);
        return toDto(saved, principal.getId(), true);
    }

    @Transactional(readOnly = true)
    public List<PollDto> getPolls(Long sessionId, UserPrincipal principal) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        checkMemberOrAdminAccess(session.getClassroom(), principal);

        boolean isTeacherOrAdmin = principal.getRole() == Role.ADMIN ||
                (principal.getRole() == Role.TEACHER && session.getClassroom().getTeacher().getId().equals(principal.getId()));

        List<Poll> polls = pollRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
        return polls.stream()
                .map(p -> toDto(p, principal.getId(), isTeacherOrAdmin))
                .toList();
    }

    @Transactional
    public PollDto vote(Long sessionId, Long pollId, CastVoteRequest request, UserPrincipal principal) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        checkMemberOrAdminAccess(session.getClassroom(), principal);

        Poll poll = pollRepository.findByIdAndSessionId(pollId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found in this session"));

        if (!"OPEN".equalsIgnoreCase(poll.getStatus())) {
            throw new BadRequestException("Poll is not open for voting");
        }

        if (pollVoteRepository.existsByPollIdAndUserId(pollId, principal.getId())) {
            throw new ConflictException("You have already voted in this poll");
        }

        PollOption option = pollOptionRepository.findById(request.getOptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Poll option not found"));

        if (!option.getPoll().getId().equals(pollId)) {
            throw new BadRequestException("Option does not belong to this poll");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PollVote vote = new PollVote(poll, option, user);
        try {
            pollVoteRepository.saveAndFlush(vote);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("You have already voted in this poll");
        }

        broadcastPollUpdate(sessionId, poll);

        boolean isTeacherOrAdmin = principal.getRole() == Role.ADMIN ||
                (principal.getRole() == Role.TEACHER && session.getClassroom().getTeacher().getId().equals(principal.getId()));
        return toDto(poll, principal.getId(), isTeacherOrAdmin);
    }

    private void broadcastPollUpdate(Long sessionId, Poll poll) {
        PollDto teacherDto = toDto(poll, null, true);
        PollDto studentDto = toDto(poll, null, false);

        // Send full results to teacher topic
        messagingTemplate.convertAndSend("/topic/sessions/" + sessionId + "/polls/teacher", teacherDto);

        // Send visibility-scoped results to general subscribers
        messagingTemplate.convertAndSend("/topic/sessions/" + sessionId + "/polls", studentDto);
    }

    private PollDto toDto(Poll poll, Long currentUserId, boolean isTeacherOrAdmin) {
        boolean showResults = isTeacherOrAdmin ||
                "CLOSED".equalsIgnoreCase(poll.getStatus()) ||
                poll.isResultsVisibleToStudents();

        Long userVotedOptionId = null;
        if (currentUserId != null) {
            Optional<PollVote> userVote = pollVoteRepository.findByPollIdAndUserId(poll.getId(), currentUserId);
            if (userVote.isPresent()) {
                userVotedOptionId = userVote.get().getOption().getId();
            }
        }

        long totalVotes = 0;
        Map<Long, Long> countsByOption = new HashMap<>();
        if (showResults) {
            List<Object[]> grouped = pollVoteRepository.countVotesGroupedByOption(poll.getId());
            for (Object[] row : grouped) {
                Long optionId = (Long) row[0];
                Long count = (Long) row[1];
                countsByOption.put(optionId, count);
                totalVotes += count;
            }
        }

        final long finalTotalVotes = totalVotes;
        List<PollOptionDto> optionDtos = poll.getOptions().stream().map(opt -> {
            Long count = showResults ? countsByOption.getOrDefault(opt.getId(), 0L) : null;
            Double percentage = null;
            if (showResults) {
                percentage = finalTotalVotes > 0 ? (count * 100.0 / finalTotalVotes) : 0.0;
            }
            return new PollOptionDto(
                    opt.getId(),
                    opt.getOptionText(),
                    opt.getOrderIndex(),
                    count,
                    percentage
            );
        }).toList();

        return new PollDto(
                poll.getId(),
                poll.getSession().getId(),
                poll.getQuestion(),
                poll.getStatus(),
                poll.isResultsVisibleToStudents(),
                showResults ? totalVotes : null,
                userVotedOptionId,
                optionDtos,
                poll.getCreatedAt()
        );
    }

    private void checkTeacherOrAdminAccess(Classroom classroom, UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (principal.getRole() != Role.TEACHER || !classroom.getTeacher().getId().equals(principal.getId())) {
            throw new ForbiddenException("You do not have permission to manage polls for this session");
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
        throw new ForbiddenException("You are not enrolled in this classroom");
    }
}
