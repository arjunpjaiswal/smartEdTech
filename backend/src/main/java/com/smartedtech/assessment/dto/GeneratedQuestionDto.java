package com.smartedtech.assessment.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class GeneratedQuestionDto {

    private String questionText;
    private String questionType; // MCQ, SUBJECTIVE, ONE_LINER
    private BigDecimal marks;
    private int orderIndex;
    private String modelAnswer;
    private String rubric;
    private List<String> topicTags = new ArrayList<>();
    private List<GeneratedOptionDto> options = new ArrayList<>();

    public GeneratedQuestionDto() {
    }

    public GeneratedQuestionDto(String questionText, String questionType, BigDecimal marks, int orderIndex,
                                String modelAnswer, String rubric, List<String> topicTags,
                                List<GeneratedOptionDto> options) {
        this.questionText = questionText;
        this.questionType = questionType;
        this.marks = marks;
        this.orderIndex = orderIndex;
        this.modelAnswer = modelAnswer;
        this.rubric = rubric;
        this.topicTags = topicTags != null ? topicTags : new ArrayList<>();
        this.options = options != null ? options : new ArrayList<>();
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public BigDecimal getMarks() {
        return marks;
    }

    public void setMarks(BigDecimal marks) {
        this.marks = marks;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public String getModelAnswer() {
        return modelAnswer;
    }

    public void setModelAnswer(String modelAnswer) {
        this.modelAnswer = modelAnswer;
    }

    public String getRubric() {
        return rubric;
    }

    public void setRubric(String rubric) {
        this.rubric = rubric;
    }

    public List<String> getTopicTags() {
        return topicTags;
    }

    public void setTopicTags(List<String> topicTags) {
        this.topicTags = topicTags;
    }

    public List<GeneratedOptionDto> getOptions() {
        return options;
    }

    public void setOptions(List<GeneratedOptionDto> options) {
        this.options = options;
    }
}
