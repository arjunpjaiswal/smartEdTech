package com.smartedtech.assessment.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class ReorderQuestionsRequest {

    @NotEmpty(message = "Question IDs list cannot be empty")
    private List<Long> questionIds;

    public ReorderQuestionsRequest() {
    }

    public ReorderQuestionsRequest(List<Long> questionIds) {
        this.questionIds = questionIds;
    }

    public List<Long> getQuestionIds() {
        return questionIds;
    }

    public void setQuestionIds(List<Long> questionIds) {
        this.questionIds = questionIds;
    }
}
