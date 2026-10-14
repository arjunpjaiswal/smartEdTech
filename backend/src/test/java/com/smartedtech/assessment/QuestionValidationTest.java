package com.smartedtech.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.assessment.dto.GeneratedQuestionDto;
import com.smartedtech.common.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class QuestionValidationTest {

    private GenerationJobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new GenerationJobService(
                null, null, null, null, null, null, null, null, null, null, new ObjectMapper()
        );
    }

    @Test
    @DisplayName("Valid JSON with MCQ and Subjective passes schema and semantic validation")
    void validQuestionsJson_ParsesCorrectly() {
        String validJson = """
        {
          "questions": [
            {
              "questionText": "What is the capital of France?",
              "questionType": "MCQ",
              "marks": 2.0,
              "orderIndex": 1,
              "topicTags": ["Geography", "Europe"],
              "options": [
                {"optionText": "Paris", "isCorrect": true, "orderIndex": 1},
                {"optionText": "London", "isCorrect": false, "orderIndex": 2},
                {"optionText": "Berlin", "isCorrect": false, "orderIndex": 3},
                {"optionText": "Madrid", "isCorrect": false, "orderIndex": 4}
              ]
            },
            {
              "questionText": "Explain the process of photosynthesis.",
              "questionType": "SUBJECTIVE",
              "marks": 5.0,
              "orderIndex": 2,
              "modelAnswer": "Photosynthesis is the process by which green plants produce carbohydrates...",
              "rubric": "2 marks for equation, 3 marks for light-dependent and dark reactions explanation",
              "topicTags": ["Biology", "Botany"],
              "options": []
            }
          ]
        }
        """;

        List<GeneratedQuestionDto> questions = jobService.parseAndValidateQuestions(validJson, 2);

        assertThat(questions).hasSize(2);
        assertThat(questions.get(0).getQuestionText()).isEqualTo("What is the capital of France?");
        assertThat(questions.get(0).getMarks()).isEqualByComparingTo(BigDecimal.valueOf(2.0));
        assertThat(questions.get(0).getOptions()).hasSize(4);
        assertThat(questions.get(0).getTopicTags()).contains("Geography", "Europe");

        assertThat(questions.get(1).getQuestionType()).isEqualTo("SUBJECTIVE");
        assertThat(questions.get(1).getModelAnswer()).isNotBlank();
    }

    @Test
    @DisplayName("MCQ with 0 correct options fails validation")
    void mcqWithNoCorrectOption_ThrowsBadRequest() {
        String invalidJson = """
        {
          "questions": [
            {
              "questionText": "Which number is even?",
              "questionType": "MCQ",
              "marks": 1.0,
              "topicTags": ["Math"],
              "options": [
                {"optionText": "1", "isCorrect": false, "orderIndex": 1},
                {"optionText": "3", "isCorrect": false, "orderIndex": 2}
              ]
            }
          ]
        }
        """;

        assertThatThrownBy(() -> jobService.parseAndValidateQuestions(invalidJson, 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("EXACTLY 1 correct option");
    }

    @Test
    @DisplayName("MCQ with 2 correct options fails validation")
    void mcqWithMultipleCorrectOptions_ThrowsBadRequest() {
        String invalidJson = """
        {
          "questions": [
            {
              "questionText": "Select an even number",
              "questionType": "MCQ",
              "marks": 1.0,
              "topicTags": ["Math"],
              "options": [
                {"optionText": "2", "isCorrect": true, "orderIndex": 1},
                {"optionText": "4", "isCorrect": true, "orderIndex": 2}
              ]
            }
          ]
        }
        """;

        assertThatThrownBy(() -> jobService.parseAndValidateQuestions(invalidJson, 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("EXACTLY 1 correct option");
    }

    @Test
    @DisplayName("Question with empty topic tags fails validation")
    void questionWithEmptyTopicTags_ThrowsBadRequest() {
        String invalidJson = """
        {
          "questions": [
            {
              "questionText": "What is 2+2?",
              "questionType": "MCQ",
              "marks": 1.0,
              "topicTags": [],
              "options": [
                {"optionText": "4", "isCorrect": true, "orderIndex": 1},
                {"optionText": "5", "isCorrect": false, "orderIndex": 2}
              ]
            }
          ]
        }
        """;

        assertThatThrownBy(() -> jobService.parseAndValidateQuestions(invalidJson, 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least one topic tag");
    }

    @Test
    @DisplayName("Question with zero or negative marks fails validation")
    void questionWithNonPositiveMarks_ThrowsBadRequest() {
        String invalidJson = """
        {
          "questions": [
            {
              "questionText": "What is 2+2?",
              "questionType": "MCQ",
              "marks": 0.0,
              "topicTags": ["Math"],
              "options": [
                {"optionText": "4", "isCorrect": true, "orderIndex": 1},
                {"optionText": "5", "isCorrect": false, "orderIndex": 2}
              ]
            }
          ]
        }
        """;

        assertThatThrownBy(() -> jobService.parseAndValidateQuestions(invalidJson, 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("marks > 0");
    }

    @Test
    @DisplayName("Subjective question missing modelAnswer fails validation")
    void subjectiveWithoutModelAnswer_ThrowsBadRequest() {
        String invalidJson = """
        {
          "questions": [
            {
              "questionText": "Describe gravity.",
              "questionType": "SUBJECTIVE",
              "marks": 3.0,
              "topicTags": ["Physics"]
            }
          ]
        }
        """;

        assertThatThrownBy(() -> jobService.parseAndValidateQuestions(invalidJson, 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("requires a non-empty 'modelAnswer'");
    }

    @Test
    @DisplayName("Malformed JSON output from LLM throws BadRequestException")
    void malformedJson_ThrowsBadRequest() {
        String brokenJson = "{ questions: [ not valid json }";

        assertThatThrownBy(() -> jobService.parseAndValidateQuestions(brokenJson, 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not valid JSON");
    }
}
