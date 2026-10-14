package com.smartedtech.assessment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AssessmentDto {

    private Long id;
    private Long classroomId;
    private String classroomTitle;
    private Long creatorId;
    private String creatorName;
    private String title;
    private String description;
    private String instructions;
    private int durationMinutes;
    private Instant dueDate;
    private int maxAttempts;
    private BigDecimal totalMarks;
    private String status;
    private int questionCount;
    private List<QuestionDto> questions = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public AssessmentDto() {
    }

    public AssessmentDto(Long id, Long classroomId, String classroomTitle, Long creatorId, String creatorName,
                         String title, String description, String instructions, int durationMinutes,
                         Instant dueDate, int maxAttempts, BigDecimal totalMarks, String status,
                         int questionCount, List<QuestionDto> questions, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.classroomId = classroomId;
        this.classroomTitle = classroomTitle;
        this.creatorId = creatorId;
        this.creatorName = creatorName;
        this.title = title;
        this.description = description;
        this.instructions = instructions;
        this.durationMinutes = durationMinutes;
        this.dueDate = dueDate;
        this.maxAttempts = maxAttempts;
        this.totalMarks = totalMarks;
        this.status = status;
        this.questionCount = questionCount;
        this.questions = questions != null ? questions : new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClassroomId() {
        return classroomId;
    }

    public void setClassroomId(Long classroomId) {
        this.classroomId = classroomId;
    }

    public String getClassroomTitle() {
        return classroomTitle;
    }

    public void setClassroomTitle(String classroomTitle) {
        this.classroomTitle = classroomTitle;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public BigDecimal getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(BigDecimal totalMarks) {
        this.totalMarks = totalMarks;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    public List<QuestionDto> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionDto> questions) {
        this.questions = questions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
