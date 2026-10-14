package com.smartedtech.assessment.dto;

import java.util.ArrayList;
import java.util.List;

public class GeneratedQuestionsResponse {

    private List<GeneratedQuestionDto> questions = new ArrayList<>();

    public GeneratedQuestionsResponse() {
    }

    public GeneratedQuestionsResponse(List<GeneratedQuestionDto> questions) {
        this.questions = questions != null ? questions : new ArrayList<>();
    }

    public List<GeneratedQuestionDto> getQuestions() {
        return questions;
    }

    public void setQuestions(List<GeneratedQuestionDto> questions) {
        this.questions = questions;
    }
}
