package com.smartedtech.assessment.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class ApplyGeneratedQuestionsRequest {

    @NotEmpty(message = "Questions list cannot be empty")
    private List<GeneratedQuestionDto> questions;

    public ApplyGeneratedQuestionsRequest() {
    }

    public ApplyGeneratedQuestionsRequest(List<GeneratedQuestionDto> questions) {
        this.questions = questions;
    }

    public List<GeneratedQuestionDto> getQuestions() {
        return questions;
    }

    public void setQuestions(List<GeneratedQuestionDto> questions) {
        this.questions = questions;
    }
}
