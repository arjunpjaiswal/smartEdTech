package com.smartedtech.poll.dto;

import java.time.Instant;
import java.util.List;

public class PollDto {

    private Long id;
    private Long sessionId;
    private String question;
    private String status;
    private boolean resultsVisibleToStudents;
    private Long totalVotes;
    private Long userVotedOptionId;
    private List<PollOptionDto> options;
    private Instant createdAt;

    public PollDto() {
    }

    public PollDto(Long id, Long sessionId, String question, String status, boolean resultsVisibleToStudents,
                   Long totalVotes, Long userVotedOptionId, List<PollOptionDto> options, Instant createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.question = question;
        this.status = status;
        this.resultsVisibleToStudents = resultsVisibleToStudents;
        this.totalVotes = totalVotes;
        this.userVotedOptionId = userVotedOptionId;
        this.options = options;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isResultsVisibleToStudents() {
        return resultsVisibleToStudents;
    }

    public void setResultsVisibleToStudents(boolean resultsVisibleToStudents) {
        this.resultsVisibleToStudents = resultsVisibleToStudents;
    }

    public Long getTotalVotes() {
        return totalVotes;
    }

    public void setTotalVotes(Long totalVotes) {
        this.totalVotes = totalVotes;
    }

    public Long getUserVotedOptionId() {
        return userVotedOptionId;
    }

    public void setUserVotedOptionId(Long userVotedOptionId) {
        this.userVotedOptionId = userVotedOptionId;
    }

    public List<PollOptionDto> getOptions() {
        return options;
    }

    public void setOptions(List<PollOptionDto> options) {
        this.options = options;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
