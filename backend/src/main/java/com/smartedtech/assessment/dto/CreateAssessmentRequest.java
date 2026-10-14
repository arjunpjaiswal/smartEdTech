package com.smartedtech.assessment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public class CreateAssessmentRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;
    private String instructions;

    @Min(value = 1, message = "Duration must be at least 1 minute")
    private int durationMinutes = 60;

    private Instant dueDate;

    @Min(value = 1, message = "Max attempts must be at least 1")
    private int maxAttempts = 1;

    public CreateAssessmentRequest() {
    }

    public CreateAssessmentRequest(String title, String description, String instructions, int durationMinutes, Instant dueDate, int maxAttempts) {
        this.title = title;
        this.description = description;
        this.instructions = instructions;
        this.durationMinutes = durationMinutes;
        this.dueDate = dueDate;
        this.maxAttempts = maxAttempts;
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
}
