package com.smartedtech.assessment.dto;

import com.smartedtech.evaluation.dto.EvaluationDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AssessmentAttemptDto {

    private Long id;
    private Long assessmentId;
    private String assessmentTitle;
    private Long studentId;
    private String studentName;
    private int attemptNumber;
    private String status; // IN_PROGRESS, SUBMITTED, EVALUATED
    private Instant startedAt;
    private Instant submittedAt;
    private BigDecimal totalScore;
    private BigDecimal maxScore;
    private List<StudentAnswerDto> answers = new ArrayList<>();
    private List<EvaluationDto> evaluations = new ArrayList<>();

    public AssessmentAttemptDto() {
    }

    public AssessmentAttemptDto(Long id, Long assessmentId, String assessmentTitle, Long studentId,
                                String studentName, int attemptNumber, String status, Instant startedAt,
                                Instant submittedAt, BigDecimal totalScore, BigDecimal maxScore,
                                List<StudentAnswerDto> answers, List<EvaluationDto> evaluations) {
        this.id = id;
        this.assessmentId = assessmentId;
        this.assessmentTitle = assessmentTitle;
        this.studentId = studentId;
        this.studentName = studentName;
        this.attemptNumber = attemptNumber;
        this.status = status;
        this.startedAt = startedAt;
        this.submittedAt = submittedAt;
        this.totalScore = totalScore;
        this.maxScore = maxScore;
        this.answers = answers != null ? answers : new ArrayList<>();
        this.evaluations = evaluations != null ? evaluations : new ArrayList<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(Long assessmentId) {
        this.assessmentId = assessmentId;
    }

    public String getAssessmentTitle() {
        return assessmentTitle;
    }

    public void setAssessmentTitle(String assessmentTitle) {
        this.assessmentTitle = assessmentTitle;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public List<StudentAnswerDto> getAnswers() {
        return answers;
    }

    public void setAnswers(List<StudentAnswerDto> answers) {
        this.answers = answers;
    }

    public List<EvaluationDto> getEvaluations() {
        return evaluations;
    }

    public void setEvaluations(List<EvaluationDto> evaluations) {
        this.evaluations = evaluations;
    }
}
