package com.smartedtech.assessment.dto;

import java.util.List;

public class SubmitAttemptRequest {

    private List<SaveAnswerRequest> answers;

    public SubmitAttemptRequest() {
    }

    public SubmitAttemptRequest(List<SaveAnswerRequest> answers) {
        this.answers = answers;
    }

    public List<SaveAnswerRequest> getAnswers() {
        return answers;
    }

    public void setAnswers(List<SaveAnswerRequest> answers) {
        this.answers = answers;
    }
}
