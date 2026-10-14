package com.smartedtech.assessment;

import com.smartedtech.analytics.RemediationPlan;
import com.smartedtech.analytics.RemediationPlanRepository;
import com.smartedtech.analytics.WeakConcept;
import com.smartedtech.analytics.WeakConceptRepository;
import com.smartedtech.analytics.dto.RemediationPlanDto;
import com.smartedtech.analytics.dto.WeakConceptDto;
import com.smartedtech.assessment.dto.*;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.common.BadRequestException;
import com.smartedtech.common.ForbiddenException;
import com.smartedtech.common.ResourceNotFoundException;
import com.smartedtech.evaluation.Evaluation;
import com.smartedtech.evaluation.EvaluationRepository;
import com.smartedtech.evaluation.dto.EvaluationDto;
import com.smartedtech.evaluation.dto.UpdateEvaluationRequest;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AssessmentAttemptService {

    private final AssessmentAttemptRepository attemptRepository;
    private final AssessmentRepository assessmentRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final EvaluationRepository evaluationRepository;
    private final ClassroomEnrollmentRepository enrollmentRepository;
    private final GenerationJobService generationJobService;
    private final RemediationPlanRepository remediationPlanRepository;
    private final WeakConceptRepository weakConceptRepository;

    public AssessmentAttemptService(
            AssessmentAttemptRepository attemptRepository,
            AssessmentRepository assessmentRepository,
            QuestionRepository questionRepository,
            AnswerOptionRepository answerOptionRepository,
            StudentAnswerRepository studentAnswerRepository,
            EvaluationRepository evaluationRepository,
            ClassroomEnrollmentRepository enrollmentRepository,
            GenerationJobService generationJobService,
            RemediationPlanRepository remediationPlanRepository,
            WeakConceptRepository weakConceptRepository) {
        this.attemptRepository = attemptRepository;
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.studentAnswerRepository = studentAnswerRepository;
        this.evaluationRepository = evaluationRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.generationJobService = generationJobService;
        this.remediationPlanRepository = remediationPlanRepository;
        this.weakConceptRepository = weakConceptRepository;
    }

    @Transactional
    public AssessmentAttemptDto startAttempt(Long assessmentId, User student) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id: " + assessmentId));

        if (!"PUBLISHED".equalsIgnoreCase(assessment.getStatus())) {
            throw new BadRequestException("Assessment is not currently published for taking");
        }

        // Check classroom enrollment
        boolean enrolled = enrollmentRepository.existsByClassroomIdAndStudentId(assessment.getClassroom().getId(), student.getId());
        if (!enrolled) {
            throw new ForbiddenException("You are not enrolled in this classroom");
        }

        // Deadline check
        if (assessment.getDueDate() != null && Instant.now().isAfter(assessment.getDueDate())) {
            throw new BadRequestException("Assessment deadline has passed on " + assessment.getDueDate());
        }

        // Check if an attempt is currently IN_PROGRESS
        Optional<AssessmentAttempt> activeAttempt = attemptRepository.findByAssessmentIdAndStudentIdAndStatus(
                assessmentId, student.getId(), "IN_PROGRESS"
        );
        if (activeAttempt.isPresent()) {
            return toDto(activeAttempt.get());
        }

        // Attempt limit check
        long attemptCount = attemptRepository.countByAssessmentIdAndStudentId(assessmentId, student.getId());
        if (attemptCount >= assessment.getMaxAttempts()) {
            throw new BadRequestException("Maximum attempt limit reached (" + assessment.getMaxAttempts() + ")");
        }

        int nextAttemptNumber = (int) attemptCount + 1;
        AssessmentAttempt attempt = new AssessmentAttempt(assessment, student, nextAttemptNumber);
        attempt = attemptRepository.save(attempt);

        return toDto(attempt);
    }

    @Transactional
    public StudentAnswerDto saveAnswer(Long attemptId, SaveAnswerRequest request, User student) {
        AssessmentAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found with id: " + attemptId));

        validateStudentAttempt(attempt, student);

        if (!"IN_PROGRESS".equalsIgnoreCase(attempt.getStatus())) {
            throw new BadRequestException("Cannot save answers for an attempt that is " + attempt.getStatus());
        }

        Assessment assessment = attempt.getAssessment();
        if (assessment.getDueDate() != null && Instant.now().isAfter(assessment.getDueDate())) {
            throw new BadRequestException("Assessment deadline has passed");
        }

        Question question = questionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + request.getQuestionId()));

        if (!question.getAssessment().getId().equals(assessment.getId())) {
            throw new BadRequestException("Question does not belong to this assessment");
        }

        AnswerOption option = null;
        if (request.getSelectedOptionId() != null) {
            option = answerOptionRepository.findById(request.getSelectedOptionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Answer option not found with id: " + request.getSelectedOptionId()));
            if (!option.getQuestion().getId().equals(question.getId())) {
                throw new BadRequestException("Option does not belong to this question");
            }
        }

        StudentAnswer studentAnswer = studentAnswerRepository.findByAttemptIdAndQuestionId(attempt.getId(), question.getId())
                .orElse(new StudentAnswer(attempt, question, null, null));

        studentAnswer.setSelectedOption(option);
        studentAnswer.setAnswerText(request.getAnswerText());
        studentAnswer = studentAnswerRepository.save(studentAnswer);

        return new StudentAnswerDto(
                studentAnswer.getId(),
                question.getId(),
                option != null ? option.getId() : null,
                studentAnswer.getAnswerText()
        );
    }

    @Transactional
    public AssessmentAttemptDto submitAttempt(Long attemptId, SubmitAttemptRequest submitRequest, User student) {
        AssessmentAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found with id: " + attemptId));

        validateStudentAttempt(attempt, student);

        // Idempotency check: if already submitted, return current result without re-grading
        if ("SUBMITTED".equalsIgnoreCase(attempt.getStatus()) || "EVALUATED".equalsIgnoreCase(attempt.getStatus())) {
            return toDto(attempt);
        }

        // Save any submitted answers in the request
        if (submitRequest != null && submitRequest.getAnswers() != null) {
            for (SaveAnswerRequest ansReq : submitRequest.getAnswers()) {
                saveAnswer(attemptId, ansReq, student);
            }
        }

        Assessment assessment = attempt.getAssessment();
        List<Question> questions = questionRepository.findByAssessmentIdOrderByOrderIndexAsc(assessment.getId());
        List<StudentAnswer> answers = studentAnswerRepository.findByAttemptId(attempt.getId());

        boolean hasSubjective = false;
        BigDecimal objectiveScoreTotal = BigDecimal.ZERO;

        for (Question q : questions) {
            if ("MCQ".equalsIgnoreCase(q.getQuestionType())) {
                // Find student answer
                StudentAnswer answer = answers.stream()
                        .filter(a -> a.getQuestion().getId().equals(q.getId()))
                        .findFirst()
                        .orElse(null);

                // Find correct option
                AnswerOption correctOption = q.getOptions().stream()
                        .filter(AnswerOption::isCorrect)
                        .findFirst()
                        .orElse(null);

                boolean isCorrect = false;
                if (answer != null && answer.getSelectedOption() != null && correctOption != null) {
                    isCorrect = answer.getSelectedOption().getId().equals(correctOption.getId());
                }

                BigDecimal score = isCorrect ? q.getMarks() : BigDecimal.ZERO;
                objectiveScoreTotal = objectiveScoreTotal.add(score);

                // Create or update evaluation
                Evaluation eval = evaluationRepository.findByAttemptIdAndQuestionId(attempt.getId(), q.getId())
                        .orElse(new Evaluation());
                eval.setAttempt(attempt);
                eval.setQuestion(q);
                eval.setScore(score.setScale(2, RoundingMode.HALF_UP));
                eval.setMaxMarks(q.getMarks());
                eval.setStatus("AUTO_GRADED");
                eval.setFeedback(isCorrect ? "Correct answer" : "Incorrect answer");
                eval.setStrengths(isCorrect ? "Mastered question concept" : null);
                eval.setMissingConcepts(isCorrect ? null : "Review question topic");
                eval.setProviderUsed("AUTO_GRADER");
                evaluationRepository.save(eval);

                // If incorrect, add weak concept
                if (!isCorrect) {
                    WeakConcept weakConcept = new WeakConcept(
                            student,
                            assessment.getClassroom(),
                            assessment.getId(),
                            q.getId(),
                            "MCQ: " + (q.getQuestionText().length() > 50 ? q.getQuestionText().substring(0, 47) + "..." : q.getQuestionText()),
                            "MODERATE",
                            "Incorrect option chosen on question " + q.getOrderIndex()
                    );
                    weakConceptRepository.save(weakConcept);
                }
            } else {
                hasSubjective = true;
            }
        }

        attempt.setSubmittedAt(Instant.now());
        attempt.setTotalScore(objectiveScoreTotal.setScale(2, RoundingMode.HALF_UP));

        if (hasSubjective) {
            attempt.setStatus("SUBMITTED");
            attempt = attemptRepository.save(attempt);

            // Trigger async subjective evaluation job
            GenerationJob job = generationJobService.createSubjectiveEvaluationJob(student, attempt);
            generationJobService.executeSubjectiveEvaluationAsync(job.getId());
        } else {
            attempt.setStatus("EVALUATED");
            attempt = attemptRepository.save(attempt);

            // MCQs only: if marks lost, trigger remediation job
            if (objectiveScoreTotal.compareTo(assessment.getTotalMarks()) < 0) {
                generationJobService.createAndTriggerRemediation(attempt);
            }
        }

        return toDto(attempt);
    }

    @Transactional(readOnly = true)
    public AssessmentAttemptDto getAttempt(Long attemptId, User user) {
        AssessmentAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found with id: " + attemptId));

        validateAccess(attempt, user);
        return toDto(attempt);
    }

    @Transactional(readOnly = true)
    public List<AssessmentAttemptDto> listAttempts(Long assessmentId, User user) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id: " + assessmentId));

        if (user.getRole() == Role.STUDENT) {
            return attemptRepository.findByAssessmentIdAndStudentIdOrderByAttemptNumberDesc(assessmentId, user.getId())
                    .stream().map(this::toDto).collect(Collectors.toList());
        } else {
            // Teacher or admin
            if (user.getRole() != Role.ADMIN && !assessment.getClassroom().getTeacher().getId().equals(user.getId())) {
                throw new ForbiddenException("You are not authorized to view all attempts for this assessment");
            }
            return attemptRepository.findAll().stream()
                    .filter(a -> a.getAssessment().getId().equals(assessmentId))
                    .map(this::toDto)
                    .collect(Collectors.toList());
        }
    }

    @Transactional
    public EvaluationDto updateEvaluation(Long evaluationId, UpdateEvaluationRequest request, User teacher) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluation not found with id: " + evaluationId));

        AssessmentAttempt attempt = evaluation.getAttempt();
        Classroom classroom = attempt.getAssessment().getClassroom();

        if (teacher.getRole() != Role.ADMIN && !classroom.getTeacher().getId().equals(teacher.getId())) {
            throw new ForbiddenException("Only the classroom teacher or admin can modify evaluations");
        }

        if (request.getScore().compareTo(evaluation.getMaxMarks()) > 0) {
            throw new BadRequestException("Score (" + request.getScore() + ") cannot exceed max marks (" + evaluation.getMaxMarks() + ")");
        }

        evaluation.setScore(request.getScore().setScale(2, RoundingMode.HALF_UP));
        if (request.getFeedback() != null) evaluation.setFeedback(request.getFeedback());
        if (request.getStrengths() != null) evaluation.setStrengths(request.getStrengths());
        if (request.getMissingConcepts() != null) evaluation.setMissingConcepts(request.getMissingConcepts());

        evaluation.setStatus("ACCEPTED".equalsIgnoreCase(evaluation.getStatus()) ? "ACCEPTED" : "OVERRIDDEN");
        evaluation.setEvaluatedBy(teacher);
        evaluation = evaluationRepository.save(evaluation);

        // Recalculate attempt score
        List<Evaluation> allEvals = evaluationRepository.findByAttemptId(attempt.getId());
        BigDecimal total = BigDecimal.ZERO;
        for (Evaluation ev : allEvals) {
            if (ev.getScore() != null) total = total.add(ev.getScore());
        }
        attempt.setTotalScore(total.setScale(2, RoundingMode.HALF_UP));
        attempt.setStatus("EVALUATED");
        attemptRepository.save(attempt);

        return toEvaluationDto(evaluation);
    }

    @Transactional(readOnly = true)
    public RemediationPlanDto getRemediationPlan(Long attemptId, User user) {
        AssessmentAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found with id: " + attemptId));

        validateAccess(attempt, user);

        RemediationPlan plan = remediationPlanRepository.findByAttemptId(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("No remediation plan generated yet for attempt: " + attemptId));

        return new RemediationPlanDto(
                plan.getId(),
                attempt.getId(),
                attempt.getStudent().getId(),
                attempt.getAssessment().getId(),
                attempt.getAssessment().getTitle(),
                plan.getSummary(),
                plan.getRecommendationsJson(),
                plan.getProviderUsed(),
                plan.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<WeakConceptDto> getWeakConceptsForStudent(Long studentId, Long classroomId, User user) {
        if (user.getRole() == Role.STUDENT && !user.getId().equals(studentId)) {
            throw new ForbiddenException("Students can only view their own weak concepts");
        }

        List<WeakConcept> list;
        if (classroomId != null) {
            list = weakConceptRepository.findByStudentIdAndClassroomId(studentId, classroomId);
        } else {
            list = weakConceptRepository.findByStudentId(studentId);
        }

        return list.stream()
                .map(wc -> new WeakConceptDto(
                        wc.getId(),
                        wc.getStudent().getId(),
                        wc.getClassroom().getId(),
                        wc.getAssessmentId(),
                        wc.getQuestionId(),
                        wc.getConceptName(),
                        wc.getSeverity(),
                        wc.getExplanation(),
                        wc.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    private void validateStudentAttempt(AssessmentAttempt attempt, User student) {
        if (!attempt.getStudent().getId().equals(student.getId())) {
            throw new ForbiddenException("You can only access your own assessment attempts");
        }
    }

    private void validateAccess(AssessmentAttempt attempt, User user) {
        if (user.getRole() == Role.ADMIN) return;
        if (attempt.getStudent().getId().equals(user.getId())) return;
        if (attempt.getAssessment().getClassroom().getTeacher().getId().equals(user.getId())) return;
        throw new ForbiddenException("You are not authorized to access this attempt");
    }

    public AssessmentAttemptDto toDto(AssessmentAttempt attempt) {
        List<StudentAnswer> answers = studentAnswerRepository.findByAttemptId(attempt.getId());
        List<StudentAnswerDto> answerDtos = answers.stream()
                .map(a -> new StudentAnswerDto(
                        a.getId(),
                        a.getQuestion().getId(),
                        a.getSelectedOption() != null ? a.getSelectedOption().getId() : null,
                        a.getAnswerText()
                ))
                .collect(Collectors.toList());

        List<Evaluation> evals = evaluationRepository.findByAttemptId(attempt.getId());
        List<EvaluationDto> evalDtos = evals.stream()
                .map(this::toEvaluationDto)
                .collect(Collectors.toList());

        return new AssessmentAttemptDto(
                attempt.getId(),
                attempt.getAssessment().getId(),
                attempt.getAssessment().getTitle(),
                attempt.getStudent().getId(),
                attempt.getStudent().getFullName(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getStartedAt(),
                attempt.getSubmittedAt(),
                attempt.getTotalScore(),
                attempt.getAssessment().getTotalMarks(),
                answerDtos,
                evalDtos
        );
    }

    public EvaluationDto toEvaluationDto(Evaluation ev) {
        return new EvaluationDto(
                ev.getId(),
                ev.getAttempt().getId(),
                ev.getQuestion().getId(),
                ev.getQuestion().getQuestionText(),
                ev.getScore(),
                ev.getMaxMarks(),
                ev.getStatus(),
                ev.getFeedback(),
                ev.getStrengths(),
                ev.getMissingConcepts(),
                ev.getEvaluatedBy() != null ? ev.getEvaluatedBy().getId() : null,
                ev.getEvaluatedBy() != null ? ev.getEvaluatedBy().getFullName() : null,
                ev.getProviderUsed(),
                ev.getCreatedAt(),
                ev.getUpdatedAt()
        );
    }
}
