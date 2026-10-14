package com.smartedtech.poll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CreatePollRequest {

    @NotBlank(message = "Question is required")
    @Size(max = 1000, message = "Question cannot exceed 1000 characters")
    private String question;

    @NotEmpty(message = "At least two options are required")
    @Size(min = 2, max = 10, message = "Poll must have between 2 and 10 options")
    private List<@NotBlank(message = "Option cannot be blank") @Size(max = 500, message = "Option cannot exceed 500 characters") String> options;

    private boolean resultsVisibleToStudents = false;

    public CreatePollRequest() {
    }

    public CreatePollRequest(String question, List<String> options, boolean resultsVisibleToStudents) {
        this.question = question;
        this.options = options;
        this.resultsVisibleToStudents = resultsVisibleToStudents;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public boolean isResultsVisibleToStudents() {
        return resultsVisibleToStudents;
    }

    public void setResultsVisibleToStudents(boolean resultsVisibleToStudents) {
        this.resultsVisibleToStudents = resultsVisibleToStudents;
    }
}
