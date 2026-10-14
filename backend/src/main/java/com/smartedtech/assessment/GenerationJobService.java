package com.smartedtech.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.ai.AiResponse;
import com.smartedtech.ai.AiService;
import com.smartedtech.ai.AiTransientException;
import com.smartedtech.analytics.RemediationPlan;
import com.smartedtech.analytics.RemediationPlanRepository;
import com.smartedtech.analytics.WeakConcept;
import com.smartedtech.analytics.WeakConceptRepository;
import com.smartedtech.assessment.dto.*;
import com.smartedtech.common.BadRequestException;
import com.smartedtech.common.ForbiddenException;
import com.smartedtech.common.IntegrationNotConfiguredException;
import com.smartedtech.common.ResourceNotFoundException;
import com.smartedtech.evaluation.Evaluation;
import com.smartedtech.evaluation.EvaluationRepository;
import com.smartedtech.material.FileStorage;
import com.smartedtech.material.StudyMaterial;
import com.smartedtech.material.StudyMaterialRepository;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class GenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);

    private final GenerationJobRepository jobRepository;
    private final StudyMaterialRepository materialRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final QuestionRepository questionRepository;
    private final EvaluationRepository evaluationRepository;
    private final WeakConceptRepository weakConceptRepository;
    private final RemediationPlanRepository remediationPlanRepository;
    private final FileStorage fileStorage;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public GenerationJobService(
            GenerationJobRepository jobRepository,
            StudyMaterialRepository materialRepository,
            AssessmentRepository assessmentRepository,
            AssessmentAttemptRepository attemptRepository,
            QuestionRepository questionRepository,
            EvaluationRepository evaluationRepository,
            WeakConceptRepository weakConceptRepository,
            RemediationPlanRepository remediationPlanRepository,
            FileStorage fileStorage,
            AiService aiService,
            ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.materialRepository = materialRepository;
        this.assessmentRepository = assessmentRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.evaluationRepository = evaluationRepository;
        this.weakConceptRepository = weakConceptRepository;
        this.remediationPlanRepository = remediationPlanRepository;
        this.fileStorage = fileStorage;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GenerationJob createQuestionGenerationJob(User user, GenerateQuestionsRequest request) {
        if (!aiService.isAnyConfigured()) {
            throw new IntegrationNotConfiguredException(
                    "AI generation is not configured. Missing: GEMINI_API_KEY or GROQ_API_KEY environment variables."
            );
        }

        StudyMaterial material = materialRepository.findById(request.getMaterialId())
                .orElseThrow(() -> new ResourceNotFoundException("Study material not found with id: " + request.getMaterialId()));

        if (user.getRole() != Role.ADMIN && !material.getClassroom().getTeacher().getId().equals(user.getId())) {
            throw new ForbiddenException("You are not authorized to generate questions from this material");
        }

        GenerationJob job = new GenerationJob("QUESTION_GENERATION", user);
        job.setMaterial(material);
        job.setStatus("PENDING");
        return jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public GenerationJobDto getJobDto(Long jobId, User user) {
        GenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Generation job not found with id: " + jobId));

        boolean isOwner = job.getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isClassTeacher = job.getMaterial() != null
                && job.getMaterial().getClassroom().getTeacher().getId().equals(user.getId());
        boolean isAssessmentTeacher = job.getAssessment() != null
                && job.getAssessment().getCreator().getId().equals(user.getId());
        boolean isAttemptStudent = job.getAttempt() != null
                && job.getAttempt().getStudent().getId().equals(user.getId());

        if (!isOwner && !isAdmin && !isClassTeacher && !isAssessmentTeacher && !isAttemptStudent) {
            throw new ForbiddenException("You are not authorized to view this generation job");
        }

        return toDto(job);
    }

    @Async("generationTaskExecutor")
    @Transactional
    public void executeQuestionGenerationAsync(Long jobId, GenerateQuestionsRequest request) {
        GenerationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        job.setStatus("RUNNING");
        jobRepository.save(job);

        try {
            StudyMaterial material = job.getMaterial();
            Resource resource = fileStorage.loadAsResource(material.getStoredFilename());
            byte[] fileBytes;
            try (InputStream is = resource.getInputStream()) {
                fileBytes = is.readAllBytes();
            }

            String prompt = buildQuestionGenerationPrompt(request);
            AiResponse aiResponse = aiService.generateQuestions(fileBytes, prompt);

            // Parse and strictly validate output
            List<GeneratedQuestionDto> validatedQuestions = parseAndValidateQuestions(aiResponse.getContent(), request.getCount());

            GeneratedQuestionsResponse response = new GeneratedQuestionsResponse(validatedQuestions);
            String resultJson = objectMapper.writeValueAsString(response);

            job.setStatus("SUCCEEDED");
            job.setProviderUsed(aiResponse.getProvider());
            job.setResultJson(resultJson);
            job.setErrorCode(null);
            job.setErrorMessage(null);
            jobRepository.save(job);

        } catch (IntegrationNotConfiguredException e) {
            log.error("AI question generation job {} failed: integration not configured", jobId);
            job.setStatus("FAILED");
            job.setErrorCode("INTEGRATION_NOT_CONFIGURED");
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
        } catch (AiTransientException e) {
            log.error("AI question generation job {} failed with transient error: {}", jobId, e.getMessage());
            job.setStatus("FAILED");
            job.setErrorCode("AI_UPSTREAM_ERROR");
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
        } catch (BadRequestException e) {
            log.error("AI question generation job {} validation failed: {}", jobId, e.getMessage());
            job.setStatus("FAILED");
            job.setErrorCode("VALIDATION_ERROR");
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
        } catch (Exception e) {
            log.error("AI question generation job {} failed with unexpected error", jobId, e);
            job.setStatus("FAILED");
            job.setErrorCode("INTERNAL_ERROR");
            job.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Unknown error during generation");
            jobRepository.save(job);
        }
    }

    @Transactional
    public GenerationJob createSubjectiveEvaluationJob(User user, AssessmentAttempt attempt) {
        GenerationJob job = new GenerationJob("SUBJECTIVE_EVALUATION", user);
        job.setAttempt(attempt);
        job.setAssessment(attempt.getAssessment());
        job.setStatus("PENDING");
        return jobRepository.save(job);
    }

    @Async("generationTaskExecutor")
    @Transactional
    public void executeSubjectiveEvaluationAsync(Long jobId) {
        GenerationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        job.setStatus("RUNNING");
        jobRepository.save(job);

        try {
            AssessmentAttempt attempt = job.getAttempt();
            List<Question> questions = questionRepository.findByAssessmentIdOrderByOrderIndexAsc(attempt.getAssessment().getId());

            String lastProvider = "UNKNOWN";
            List<Evaluation> savedEvaluations = new ArrayList<>();

            for (Question question : questions) {
                if ("MCQ".equalsIgnoreCase(question.getQuestionType())) {
                    continue; // MCQs are already auto-graded
                }

                // Find student answer
                String studentText = "";
                for (StudentAnswer ans : attempt.getAnswers()) {
                    if (ans.getQuestion().getId().equals(question.getId())) {
                        studentText = ans.getAnswerText() != null ? ans.getAnswerText() : "";
                        break;
                    }
                }

                String rubric = question.getRubric() != null ? question.getRubric() : "Standard academic grading criteria";
                String modelAns = question.getModelAnswer() != null ? question.getModelAnswer() : "";

                AiResponse aiResponse = aiService.evaluateSubjective(rubric, question.getQuestionText(), modelAns, studentText);
                lastProvider = aiResponse.getProvider();

                // Parse and validate evaluation response
                EvaluationResult evalResult = parseAndValidateEvaluation(aiResponse.getContent(), question.getMarks());

                // Save or update evaluation
                Evaluation evaluation = evaluationRepository.findByAttemptIdAndQuestionId(attempt.getId(), question.getId())
                        .orElse(new Evaluation());
                evaluation.setAttempt(attempt);
                evaluation.setQuestion(question);
                evaluation.setScore(evalResult.suggestedMarks);
                evaluation.setMaxMarks(question.getMarks());
                evaluation.setStatus("AI_SUGGESTED");
                evaluation.setFeedback(evalResult.explanation);
                evaluation.setStrengths(evalResult.strengths);
                evaluation.setMissingConcepts(evalResult.missingConcepts);
                evaluation.setProviderUsed(aiResponse.getProvider());
                evaluation = evaluationRepository.save(evaluation);
                savedEvaluations.add(evaluation);

                // Save weak concepts if any tags present
                if (evalResult.weakConceptTags != null && !evalResult.weakConceptTags.isEmpty()) {
                    for (String tag : evalResult.weakConceptTags) {
                        if (tag != null && !tag.trim().isEmpty()) {
                            WeakConcept weakConcept = new WeakConcept(
                                    attempt.getStudent(),
                                    attempt.getAssessment().getClassroom(),
                                    attempt.getAssessment().getId(),
                                    question.getId(),
                                    tag.trim(),
                                    "MODERATE",
                                    evalResult.missingConcepts
                            );
                            weakConceptRepository.save(weakConcept);
                        }
                    }
                }
            }

            // Recalculate total score for attempt
            List<Evaluation> allEvaluations = evaluationRepository.findByAttemptId(attempt.getId());
            BigDecimal totalScore = BigDecimal.ZERO;
            for (Evaluation ev : allEvaluations) {
                if (ev.getScore() != null) {
                    totalScore = totalScore.add(ev.getScore());
                }
            }
            attempt.setTotalScore(totalScore);
            attempt.setStatus("EVALUATED");
            attemptRepository.save(attempt);

            job.setStatus("SUCCEEDED");
            job.setProviderUsed(lastProvider);
            job.setResultJson("{\"evaluatedQuestions\":" + savedEvaluations.size() + "}");
            jobRepository.save(job);

            // Trigger remediation generation job
            createAndTriggerRemediation(attempt);

        } catch (IntegrationNotConfiguredException e) {
            log.error("Subjective evaluation job {} failed: integration not configured", jobId);
            job.setStatus("FAILED");
            job.setErrorCode("INTEGRATION_NOT_CONFIGURED");
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
        } catch (AiTransientException e) {
            log.error("Subjective evaluation job {} failed with transient error: {}", jobId, e.getMessage());
            job.setStatus("FAILED");
            job.setErrorCode("AI_UPSTREAM_ERROR");
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
        } catch (Exception e) {
            log.error("Subjective evaluation job {} failed with unexpected error", jobId, e);
            job.setStatus("FAILED");
            job.setErrorCode("INTERNAL_ERROR");
            job.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Unknown error during evaluation");
            jobRepository.save(job);
        }
    }

    public void createAndTriggerRemediation(AssessmentAttempt attempt) {
        try {
            GenerationJob remJob = new GenerationJob("REMEDIATION_GENERATION", attempt.getStudent());
            remJob.setAttempt(attempt);
            remJob.setAssessment(attempt.getAssessment());
            remJob.setStatus("PENDING");
            remJob = jobRepository.save(remJob);
            executeRemediationAsync(remJob.getId());
        } catch (Exception e) {
            log.error("Failed to trigger remediation job for attempt {}: {}", attempt.getId(), e.getMessage());
        }
    }

    @Async("generationTaskExecutor")
    @Transactional
    public void executeRemediationAsync(Long jobId) {
        GenerationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        job.setStatus("RUNNING");
        jobRepository.save(job);

        try {
            AssessmentAttempt attempt = job.getAttempt();
            List<Evaluation> evaluations = evaluationRepository.findByAttemptId(attempt.getId());
            List<WeakConcept> weakConcepts = weakConceptRepository.findByStudentIdAndAssessmentId(
                    attempt.getStudent().getId(), attempt.getAssessment().getId()
            );

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Assessment: ").append(attempt.getAssessment().getTitle()).append("\n");
            promptBuilder.append("Classroom: ").append(attempt.getAssessment().getClassroom().getName()).append("\n");
            promptBuilder.append("Total Score: ").append(attempt.getTotalScore()).append(" / ").append(attempt.getAssessment().getTotalMarks()).append("\n\n");

            promptBuilder.append("Identified Weak Concepts:\n");
            if (weakConcepts.isEmpty()) {
                promptBuilder.append("None explicitly recorded.\n");
            } else {
                for (WeakConcept wc : weakConcepts) {
                    promptBuilder.append("- ").append(wc.getConceptName()).append(" (Severity: ").append(wc.getSeverity()).append("): ").append(wc.getExplanation()).append("\n");
                }
            }

            promptBuilder.append("\nEvaluation Breakdown:\n");
            for (Evaluation ev : evaluations) {
                promptBuilder.append("Question: ").append(ev.getQuestion().getQuestionText()).append("\n");
                promptBuilder.append("Score: ").append(ev.getScore()).append(" / ").append(ev.getMaxMarks()).append("\n");
                if (ev.getMissingConcepts() != null && !ev.getMissingConcepts().isEmpty()) {
                    promptBuilder.append("Missing Concepts: ").append(ev.getMissingConcepts()).append("\n");
                }
                if (ev.getFeedback() != null && !ev.getFeedback().isEmpty()) {
                    promptBuilder.append("Feedback: ").append(ev.getFeedback()).append("\n");
                }
                promptBuilder.append("\n");
            }

            AiResponse aiResponse = aiService.generateRemediation(promptBuilder.toString());

            // Validate remediation plan format
            String cleanedJson = cleanJson(aiResponse.getContent());
            JsonNode root = objectMapper.readTree(cleanedJson);
            if (!root.has("summary") || !root.has("recommendations")) {
                throw new BadRequestException("Remediation JSON must contain 'summary' and 'recommendations' fields");
            }
            String summary = root.get("summary").asText();
            String recommendationsJson = root.get("recommendations").toString();

            // Save or update RemediationPlan
            RemediationPlan plan = remediationPlanRepository.findByAttemptId(attempt.getId())
                    .orElse(new RemediationPlan());
            plan.setAttempt(attempt);
            plan.setStudent(attempt.getStudent());
            plan.setAssessment(attempt.getAssessment());
            plan.setSummary(summary);
            plan.setRecommendationsJson(recommendationsJson);
            plan.setProviderUsed(aiResponse.getProvider());
            remediationPlanRepository.save(plan);

            job.setStatus("SUCCEEDED");
            job.setProviderUsed(aiResponse.getProvider());
            job.setResultJson(cleanedJson);
            jobRepository.save(job);

        } catch (IntegrationNotConfiguredException e) {
            log.error("Remediation job {} failed: integration not configured", jobId);
            job.setStatus("FAILED");
            job.setErrorCode("INTEGRATION_NOT_CONFIGURED");
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
        } catch (AiTransientException e) {
            log.error("Remediation job {} failed with transient error: {}", jobId, e.getMessage());
            job.setStatus("FAILED");
            job.setErrorCode("AI_UPSTREAM_ERROR");
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
        } catch (Exception e) {
            log.error("Remediation job {} failed with unexpected error", jobId, e);
            job.setStatus("FAILED");
            job.setErrorCode("INTERNAL_ERROR");
            job.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Unknown error during remediation");
            jobRepository.save(job);
        }
    }

    @Transactional
    public GenerationJob retryJob(Long jobId, User user) {
        GenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Generation job not found with id: " + jobId));

        boolean isOwner = job.getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("You are not authorized to retry this generation job");
        }

        if (!"FAILED".equalsIgnoreCase(job.getStatus())) {
            throw new BadRequestException("Only FAILED generation jobs can be retried. Current status: " + job.getStatus());
        }

        job.setStatus("PENDING");
        job.setErrorCode(null);
        job.setErrorMessage(null);
        GenerationJob saved = jobRepository.save(job);

        if ("QUESTION_GENERATION".equalsIgnoreCase(saved.getJobType())) {
            GenerateQuestionsRequest request = new GenerateQuestionsRequest(
                    saved.getMaterial().getId(), 5, "MEDIUM", "MIXED", ""
            );
            executeQuestionGenerationAsync(saved.getId(), request);
        } else if ("SUBJECTIVE_EVALUATION".equalsIgnoreCase(saved.getJobType())) {
            executeSubjectiveEvaluationAsync(saved.getId());
        } else if ("REMEDIATION_GENERATION".equalsIgnoreCase(saved.getJobType())) {
            executeRemediationAsync(saved.getId());
        }

        return saved;
    }

    private String buildQuestionGenerationPrompt(GenerateQuestionsRequest request) {
        return String.format(
                "You are an expert academic curriculum designer and test creator.\n" +
                "Generate exactly %d questions based on the provided material.\n" +
                "Difficulty level: %s.\n" +
                "Requested question types: %s.\n" +
                "Additional instructions: %s.\n\n" +
                "Respond ONLY with a valid JSON object matching this schema:\n" +
                "{\n" +
                "  \"questions\": [\n" +
                "    {\n" +
                "      \"questionText\": \"String\",\n" +
                "      \"questionType\": \"MCQ\" | \"SUBJECTIVE\" | \"ONE_LINER\",\n" +
                "      \"marks\": 2.0,\n" +
                "      \"orderIndex\": 1,\n" +
                "      \"modelAnswer\": \"String (required for SUBJECTIVE/ONE_LINER)\",\n" +
                "      \"rubric\": \"String\",\n" +
                "      \"topicTags\": [\"Topic1\", \"Topic2\"],\n" +
                "      \"options\": [\n" +
                "        {\"optionText\": \"Option 1\", \"isCorrect\": true, \"orderIndex\": 1},\n" +
                "        {\"optionText\": \"Option 2\", \"isCorrect\": false, \"orderIndex\": 2},\n" +
                "        {\"optionText\": \"Option 3\", \"isCorrect\": false, \"orderIndex\": 3},\n" +
                "        {\"optionText\": \"Option 4\", \"isCorrect\": false, \"orderIndex\": 4}\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +
                "STRICT VALIDATION RULES:\n" +
                "1. For MCQ, provide exactly 4 options. EXACTLY ONE option must have isCorrect=true.\n" +
                "2. Every question must have positive marks (> 0).\n" +
                "3. topicTags must be a non-empty list of concept tags for every question.\n" +
                "4. For non-MCQ questions (SUBJECTIVE, ONE_LINER), options must be an empty list [] and modelAnswer must be present.\n" +
                "5. Return pure JSON only. No markdown, no preface, no trailing text.",
                request.getCount(),
                request.getDifficulty(),
                request.getQuestionTypes(),
                request.getInstructions() != null ? request.getInstructions() : "None"
        );
    }

    public List<GeneratedQuestionDto> parseAndValidateQuestions(String rawJson, int expectedCount) {
        String cleaned = cleanJson(rawJson);
        JsonNode root;
        try {
            root = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            throw new BadRequestException("AI output is not valid JSON: " + e.getMessage());
        }

        JsonNode questionsNode = root.has("questions") ? root.get("questions") : root;
        if (!questionsNode.isArray() || questionsNode.isEmpty()) {
            throw new BadRequestException("AI output does not contain a non-empty 'questions' array");
        }

        List<GeneratedQuestionDto> result = new ArrayList<>();
        int index = 1;
        for (JsonNode qNode : questionsNode) {
            String qText = qNode.path("questionText").asText(null);
            if (qText == null || qText.trim().isEmpty()) {
                throw new BadRequestException("Question at index " + index + " is missing required 'questionText'");
            }

            String qType = qNode.path("questionType").asText("MCQ").toUpperCase();
            if (!qType.equals("MCQ") && !qType.equals("SUBJECTIVE") && !qType.equals("ONE_LINER")) {
                throw new BadRequestException("Question at index " + index + " has invalid type '" + qType + "'. Allowed: MCQ, SUBJECTIVE, ONE_LINER");
            }

            BigDecimal marks = BigDecimal.valueOf(qNode.path("marks").asDouble(1.0)).setScale(2, RoundingMode.HALF_UP);
            if (marks.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Question at index " + index + " must have marks > 0, got: " + marks);
            }

            List<String> topicTags = new ArrayList<>();
            JsonNode tagsNode = qNode.path("topicTags");
            if (tagsNode.isArray()) {
                for (JsonNode t : tagsNode) {
                    String tag = t.asText().trim();
                    if (!tag.isEmpty()) topicTags.add(tag);
                }
            }
            if (topicTags.isEmpty()) {
                throw new BadRequestException("Question at index " + index + " must have at least one topic tag in 'topicTags'");
            }

            String modelAnswer = qNode.path("modelAnswer").asText(null);
            String rubric = qNode.path("rubric").asText(null);

            List<GeneratedOptionDto> options = new ArrayList<>();
            if ("MCQ".equals(qType)) {
                JsonNode optsNode = qNode.path("options");
                if (!optsNode.isArray() || optsNode.size() < 2) {
                    throw new BadRequestException("MCQ Question at index " + index + " must have at least 2 options");
                }
                int correctCount = 0;
                int optIdx = 1;
                for (JsonNode opt : optsNode) {
                    String optText = opt.path("optionText").asText(null);
                    if (optText == null || optText.trim().isEmpty()) {
                        throw new BadRequestException("MCQ Question at index " + index + " has an option with empty text");
                    }
                    boolean isCorrect = opt.path("isCorrect").asBoolean(false);
                    if (isCorrect) correctCount++;
                    options.add(new GeneratedOptionDto(optText.trim(), isCorrect, optIdx++));
                }
                if (correctCount != 1) {
                    throw new BadRequestException("MCQ Question at index " + index + " must have EXACTLY 1 correct option, found: " + correctCount);
                }
            } else {
                if (modelAnswer == null || modelAnswer.trim().isEmpty()) {
                    throw new BadRequestException(qType + " Question at index " + index + " requires a non-empty 'modelAnswer'");
                }
            }

            result.add(new GeneratedQuestionDto(qText.trim(), qType, marks, index, modelAnswer, rubric, topicTags, options));
            index++;
        }

        return result;
    }

    private EvaluationResult parseAndValidateEvaluation(String rawJson, BigDecimal maxMarks) {
        String cleaned = cleanJson(rawJson);
        JsonNode root;
        try {
            root = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            throw new BadRequestException("AI evaluation output is not valid JSON: " + e.getMessage());
        }

        double suggested = root.path("suggestedMarks").asDouble(0.0);
        BigDecimal score = BigDecimal.valueOf(suggested).setScale(2, RoundingMode.HALF_UP);
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            score = BigDecimal.ZERO;
        }
        if (score.compareTo(maxMarks) > 0) {
            score = maxMarks;
        }

        String explanation = root.path("explanation").asText("Evaluated based on model answer and rubric.");
        String strengths = root.path("strengths").asText("Demonstrated understanding of core concepts.");
        String missingConcepts = root.path("missingConcepts").asText("None");

        List<String> tags = new ArrayList<>();
        JsonNode tagsNode = root.path("weakConceptTags");
        if (tagsNode.isArray()) {
            for (JsonNode t : tagsNode) {
                String tag = t.asText().trim();
                if (!tag.isEmpty()) tags.add(tag);
            }
        }

        return new EvaluationResult(score, explanation, strengths, missingConcepts, tags);
    }

    public static String cleanJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private GenerationJobDto toDto(GenerationJob job) {
        return new GenerationJobDto(
                job.getId(),
                job.getJobType(),
                job.getStatus(),
                job.getUser().getId(),
                job.getMaterial() != null ? job.getMaterial().getId() : null,
                job.getAssessment() != null ? job.getAssessment().getId() : null,
                job.getAttempt() != null ? job.getAttempt().getId() : null,
                job.getProviderUsed(),
                job.getResultJson(),
                job.getErrorCode(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private static class EvaluationResult {
        final BigDecimal suggestedMarks;
        final String explanation;
        final String strengths;
        final String missingConcepts;
        final List<String> weakConceptTags;

        EvaluationResult(BigDecimal suggestedMarks, String explanation, String strengths,
                         String missingConcepts, List<String> weakConceptTags) {
            this.suggestedMarks = suggestedMarks;
            this.explanation = explanation;
            this.strengths = strengths;
            this.missingConcepts = missingConcepts;
            this.weakConceptTags = weakConceptTags;
        }
    }
}
