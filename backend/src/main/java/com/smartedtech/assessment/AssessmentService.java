package com.smartedtech.assessment;

import com.smartedtech.assessment.dto.*;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.common.BadRequestException;
import com.smartedtech.common.ForbiddenException;
import com.smartedtech.common.ResourceNotFoundException;
import com.smartedtech.notification.NotificationService;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomEnrollmentRepository enrollmentRepository;
    private final NotificationService notificationService;

    public AssessmentService(
            AssessmentRepository assessmentRepository,
            QuestionRepository questionRepository,
            AnswerOptionRepository answerOptionRepository,
            ClassroomRepository classroomRepository,
            ClassroomEnrollmentRepository enrollmentRepository,
            NotificationService notificationService) {
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.classroomRepository = classroomRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public AssessmentDto createAssessment(Long classroomId, CreateAssessmentRequest request, User user) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found with id: " + classroomId));

        validateTeacherOrAdmin(classroom, user);

        Assessment assessment = new Assessment(
                classroom,
                user,
                request.getTitle(),
                request.getDescription(),
                request.getInstructions(),
                request.getDurationMinutes(),
                request.getDueDate(),
                request.getMaxAttempts()
        );
        assessment.setStatus("DRAFT");
        assessment = assessmentRepository.save(assessment);

        return toDto(assessment, user);
    }

    @Transactional(readOnly = true)
    public AssessmentDto getAssessment(Long assessmentId, User user) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id: " + assessmentId));

        validateAccess(assessment.getClassroom(), user);

        if (user.getRole() == Role.STUDENT && "DRAFT".equalsIgnoreCase(assessment.getStatus())) {
            throw new ForbiddenException("Assessment is still in draft state");
        }

        return toDto(assessment, user);
    }

    @Transactional(readOnly = true)
    public Page<AssessmentDto> listAssessments(Long classroomId, User user, Pageable pageable) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found with id: " + classroomId));

        validateAccess(classroom, user);

        if (user.getRole() == Role.STUDENT) {
            // Students only see PUBLISHED or CLOSED assessments
            return assessmentRepository.findByClassroomIdAndStatus(classroomId, "PUBLISHED", pageable)
                    .map(a -> toDto(a, user));
        }

        return assessmentRepository.findByClassroomId(classroomId, pageable)
                .map(a -> toDto(a, user));
    }

    @Transactional
    public AssessmentDto updateAssessment(Long assessmentId, UpdateAssessmentRequest request, User user) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id: " + assessmentId));

        validateTeacherOrAdmin(assessment.getClassroom(), user);

        if ("CLOSED".equalsIgnoreCase(assessment.getStatus()) || "ARCHIVED".equalsIgnoreCase(assessment.getStatus())) {
            throw new BadRequestException("Cannot update an assessment that is " + assessment.getStatus());
        }

        assessment.setTitle(request.getTitle());
        assessment.setDescription(request.getDescription());
        assessment.setInstructions(request.getInstructions());
        assessment.setDurationMinutes(request.getDurationMinutes());
        assessment.setDueDate(request.getDueDate());
        assessment.setMaxAttempts(request.getMaxAttempts());

        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            assessment.setStatus(request.getStatus().toUpperCase());
        }

        assessment = assessmentRepository.save(assessment);
        return toDto(assessment, user);
    }

    @Transactional
    public QuestionDto addQuestion(Long assessmentId, CreateQuestionRequest request, User user) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id: " + assessmentId));

        validateTeacherOrAdmin(assessment.getClassroom(), user);

        if (!"DRAFT".equalsIgnoreCase(assessment.getStatus())) {
            throw new BadRequestException("Questions can only be added to DRAFT assessments");
        }

        Question question = new Question(
                assessment,
                request.getQuestionText(),
                request.getQuestionType().toUpperCase(),
                request.getMarks(),
                request.getOrderIndex(),
                request.getModelAnswer(),
                request.getRubric()
        );

        if ("MCQ".equalsIgnoreCase(question.getQuestionType()) && request.getOptions() != null) {
            int optOrder = 1;
            for (OptionRequest optReq : request.getOptions()) {
                AnswerOption opt = new AnswerOption(question, optReq.getOptionText(), optReq.isCorrect(), optOrder++);
                question.getOptions().add(opt);
            }
        }

        question = questionRepository.save(question);
        recalculateTotalMarks(assessment);

        return toQuestionDto(question, user);
    }

    @Transactional
    public QuestionDto updateQuestion(Long assessmentId, Long questionId, UpdateQuestionRequest request, User user) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id: " + assessmentId));

        validateTeacherOrAdmin(assessment.getClassroom(), user);

        if (!"DRAFT".equalsIgnoreCase(assessment.getStatus())) {
            throw new BadRequestException("Questions can only be modified in DRAFT assessments");
        }

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + questionId));

        if (!question.getAssessment().getId().equals(assessmentId)) {
            throw new BadRequestException("Question does not belong to this assessment");
        }

        question.setQuestionText(request.getQuestionText());
        question.setQuestionType(request.getQuestionType().toUpperCase());
        question.setMarks(request.getMarks());
        question.setOrderIndex(request.getOrderIndex());
        question.setModelAnswer(request.getModelAnswer());
        question.setRubric(request.getRubric());

        // Update options
        question.getOptions().clear();
        if ("MCQ".equalsIgnoreCase(question.getQuestionType()) && request.getOptions() != null) {
            int optOrder = 1;
            for (OptionRequest optReq : request.getOptions()) {
                AnswerOption opt = new AnswerOption(question, optReq.getOptionText(), optReq.isCorrect(), optOrder++);
                question.getOptions().add(opt);
            }
        }

        question = questionRepository.save(question);
        recalculateTotalMarks(assessment);

        return toQuestionDto(question, user);
    }

    @Transactional
    public void deleteQuestion(Long assessmentId, Long questionId, User user) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id: " + assessmentId));

        validateTeacherOrAdmin(assessment.getClassroom(), user);

        if (!"DRAFT".equalsIgnoreCase(assessment.getStatus())) {
            throw new BadRequestException("Questions can only be deleted from DRAFT assessments");
        }

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + questionId));

        if (!question.getAssessment().getId().equals(assessmentId)) {
            throw new BadRequestException("Question does not belong to this assessment");
        }

        questionRepository.delete(question);
        recalculateTotalMarks(assessment);
    }

    @Transactional
    public void reorderQuestions(Long assessmentId, ReorderQuestionsRequest request, User user) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id: " + assessmentId));

        validateTeacherOrAdmin(assessment.getClassroom(), user);

        if (!"DRAFT".equalsIgnoreCase(assessment.getStatus())) {
            throw new BadRequestException("Questions can only be reordered in DRAFT assessments");
        }

        List<Question> questions = questionRepository.findByAssessmentIdOrderByOrderIndexAsc(assessmentId);
        int index = 1;
        for (Long qId : request.getQuestionIds()) {
            for (Question q : questions) {
                if (q.getId().equals(qId)) {
                    q.setOrderIndex(index++);
                    questionRepository.save(q);
                    break;
                }
            }
        }
    }

    @Transactional
    public AssessmentDto applyGeneratedQuestions(Long assessmentId, ApplyGeneratedQuestionsRequest request, User user) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id: " + assessmentId));

        validateTeacherOrAdmin(assessment.getClassroom(), user);

        if (!"DRAFT".equalsIgnoreCase(assessment.getStatus())) {
            throw new BadRequestException("Generated questions can only be applied to DRAFT assessments");
        }

        int currentOrder = (int) questionRepository.countByAssessmentId(assessmentId) + 1;

        for (GeneratedQuestionDto gq : request.getQuestions()) {
            Question question = new Question(
                    assessment,
                    gq.getQuestionText(),
                    gq.getQuestionType().toUpperCase(),
                    gq.getMarks() != null ? gq.getMarks() : BigDecimal.ONE,
                    currentOrder++,
                    gq.getModelAnswer(),
                    gq.getRubric()
            );

            if ("MCQ".equalsIgnoreCase(gq.getQuestionType()) && gq.getOptions() != null) {
                int optIdx = 1;
                for (GeneratedOptionDto opt : gq.getOptions()) {
                    AnswerOption option = new AnswerOption(question, opt.getOptionText(), opt.isCorrect(), optIdx++);
                    question.getOptions().add(option);
                }
            }

            questionRepository.save(question);
        }

        recalculateTotalMarks(assessment);
        return toDto(assessment, user);
    }

    @Transactional
    public AssessmentDto publishAssessment(Long assessmentId, User user) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id: " + assessmentId));

        validateTeacherOrAdmin(assessment.getClassroom(), user);

        if (!"DRAFT".equalsIgnoreCase(assessment.getStatus())) {
            throw new BadRequestException("Only DRAFT assessments can be published. Current status: " + assessment.getStatus());
        }

        long questionCount = questionRepository.countByAssessmentId(assessmentId);
        if (questionCount == 0) {
            throw new BadRequestException("Cannot publish assessment: at least one valid question is required");
        }

        assessment.setStatus("PUBLISHED");
        assessment = assessmentRepository.save(assessment);

        // Notify enrolled students
        notificationService.notifyClassroomStudents(
                assessment.getClassroom(),
                "ASSESSMENT",
                "New Assessment Published: " + assessment.getTitle(),
                "Assessment '" + assessment.getTitle() + "' has been published in " + assessment.getClassroom().getName()
        );

        return toDto(assessment, user);
    }

    @Transactional
    public AssessmentDto closeAssessment(Long assessmentId, User user) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id: " + assessmentId));

        validateTeacherOrAdmin(assessment.getClassroom(), user);
        assessment.setStatus("CLOSED");
        assessment = assessmentRepository.save(assessment);

        return toDto(assessment, user);
    }

    @Transactional
    public AssessmentDto archiveAssessment(Long assessmentId, User user) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id: " + assessmentId));

        validateTeacherOrAdmin(assessment.getClassroom(), user);
        assessment.setStatus("ARCHIVED");
        assessment = assessmentRepository.save(assessment);

        return toDto(assessment, user);
    }

    private void recalculateTotalMarks(Assessment assessment) {
        List<Question> questions = questionRepository.findByAssessmentIdOrderByOrderIndexAsc(assessment.getId());
        BigDecimal total = BigDecimal.ZERO;
        for (Question q : questions) {
            if (q.getMarks() != null) {
                total = total.add(q.getMarks());
            }
        }
        assessment.setTotalMarks(total.setScale(2, RoundingMode.HALF_UP));
        assessmentRepository.save(assessment);
    }

    private void validateTeacherOrAdmin(Classroom classroom, User user) {
        if (user.getRole() == Role.ADMIN) return;
        if (!classroom.getTeacher().getId().equals(user.getId())) {
            throw new ForbiddenException("Only the classroom teacher or admin can modify assessments");
        }
    }

    private void validateAccess(Classroom classroom, User user) {
        if (user.getRole() == Role.ADMIN) return;
        if (classroom.getTeacher().getId().equals(user.getId())) return;

        boolean enrolled = enrollmentRepository.existsByClassroomIdAndStudentId(classroom.getId(), user.getId());
        if (!enrolled) {
            throw new ForbiddenException("You are not enrolled in this classroom");
        }
    }

    public AssessmentDto toDto(Assessment assessment, User user) {
        List<Question> questions = questionRepository.findByAssessmentIdOrderByOrderIndexAsc(assessment.getId());
        List<QuestionDto> questionDtos = questions.stream()
                .map(q -> toQuestionDto(q, user))
                .collect(Collectors.toList());

        return new AssessmentDto(
                assessment.getId(),
                assessment.getClassroom().getId(),
                assessment.getClassroom().getName(),
                assessment.getCreator().getId(),
                assessment.getCreator().getFullName(),
                assessment.getTitle(),
                assessment.getDescription(),
                assessment.getInstructions(),
                assessment.getDurationMinutes(),
                assessment.getDueDate(),
                assessment.getMaxAttempts(),
                assessment.getTotalMarks(),
                assessment.getStatus(),
                questions.size(),
                questionDtos,
                assessment.getCreatedAt(),
                assessment.getUpdatedAt()
        );
    }

    public QuestionDto toQuestionDto(Question q, User user) {
        boolean isStudent = (user.getRole() == Role.STUDENT);

        List<AnswerOptionDto> optionDtos = q.getOptions().stream()
                .map(opt -> new AnswerOptionDto(
                        opt.getId(),
                        opt.getOptionText(),
                        isStudent ? null : opt.isCorrect(), // Students NEVER receive answer keys!
                        opt.getOrderIndex()
                ))
                .collect(Collectors.toList());

        return new QuestionDto(
                q.getId(),
                q.getAssessment().getId(),
                q.getQuestionText(),
                q.getQuestionType(),
                q.getMarks(),
                q.getOrderIndex(),
                isStudent ? null : q.getModelAnswer(), // Student never receives model answer!
                isStudent ? null : q.getRubric(),      // Student never receives rubric!
                optionDtos
        );
    }
}
