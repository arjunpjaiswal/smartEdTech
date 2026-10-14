package com.smartedtech.assessment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class GenerateQuestionsRequest {

    @NotNull(message = "Material ID is required")
    private Long materialId;

    @Min(value = 1, message = "Question count must be at least 1")
    @Max(value = 50, message = "Question count cannot exceed 50")
    private int count = 5;

    private String difficulty = "MEDIUM"; // EASY, MEDIUM, HARD
    private String questionTypes = "MIXED"; // MCQ, SUBJECTIVE, ONE_LINER, MIXED
    private String instructions;

    public GenerateQuestionsRequest() {
    }

    public GenerateQuestionsRequest(Long materialId, int count, String difficulty, String questionTypes, String instructions) {
        this.materialId = materialId;
        this.count = count;
        this.difficulty = difficulty != null ? difficulty : "MEDIUM";
        this.questionTypes = questionTypes != null ? questionTypes : "MIXED";
        this.instructions = instructions;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getQuestionTypes() {
        return questionTypes;
    }

    public void setQuestionTypes(String questionTypes) {
        this.questionTypes = questionTypes;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }
}
