package com.smartedtech.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class GroqProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GroqProvider.class);

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GroqProvider(
            @Value("${app.integrations.groq.api-key:}") String apiKey,
            @Value("${app.integrations.groq.model:llama-3.3-70b-versatile}") String model,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null && !model.isBlank() ? model.trim() : "llama-3.3-70b-versatile";
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String getProviderName() {
        return "GROQ";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public AiResponse generateQuestionsFromPdf(byte[] pdfBytes, String prompt) {
        throw new UnsupportedOperationException("Groq does not support binary PDF inputs directly. Extracted text must be passed.");
    }

    @Override
    public AiResponse generateQuestionsFromText(String text, String prompt) {
        return executeChatCompletion(
                "You are an expert educational assessment generator. Output pure valid JSON matching the requested structure without markdown fences or extra explanations.",
                prompt + "\n\nSource Material Text:\n" + text
        );
    }

    @Override
    public AiResponse evaluateSubjective(String rubric, String questionText, String modelAnswer, String studentAnswer) {
        String userPrompt = String.format("""
                Evaluate the student's answer based on the rubric and model answer.
                Question: %s
                Model Answer: %s
                Rubric / Criteria: %s
                Student Answer: %s
                
                Return JSON with:
                - suggestedMarks (number)
                - maxMarks (number)
                - explanation (string)
                - strengths (string)
                - missingConcepts (string)
                - weakConceptTags (array of strings)
                """, questionText, modelAnswer, rubric != null ? rubric : "None", studentAnswer);

        return executeChatCompletion(
                "You are an objective academic evaluator. Grade strictly based on the rubric. Output pure valid JSON.",
                userPrompt
        );
    }

    @Override
    public AiResponse generateRemediation(String prompt) {
        return executeChatCompletion(
                "You are an adaptive learning specialist. Provide structured student remediation in JSON.",
                prompt
        );
    }

    @Override
    public AiResponse generateChatResponse(String systemPrompt, String contextText, java.util.List<ChatHistoryItem> history, String userMessage) {
        if (!isConfigured()) {
            throw new AiPermanentException("GROQ", 503, "Groq API key is not configured");
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);

        ArrayNode messages = requestBody.putArray("messages");

        String effectiveSystem = systemPrompt != null ? systemPrompt : "You are an AI Study Buddy.";
        if (contextText != null && !contextText.isBlank()) {
            effectiveSystem += "\n\nReference Study Material Content:\n" + contextText;
        }

        ObjectNode sysMsg = messages.addObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", effectiveSystem);

        if (history != null) {
            for (ChatHistoryItem item : history) {
                ObjectNode turn = messages.addObject();
                turn.put("role", "assistant".equalsIgnoreCase(item.role()) ? "assistant" : "user");
                turn.put("content", item.content());
            }
        }

        ObjectNode usrMsg = messages.addObject();
        usrMsg.put("role", "user");
        usrMsg.put("content", userMessage);

        return executeCall(requestBody);
    }

    private AiResponse executeChatCompletion(String systemMessage, String userMessage) {
        if (!isConfigured()) {
            throw new AiPermanentException("GROQ", 503, "Groq API key is not configured");
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode sysMsg = messages.addObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemMessage);

        ObjectNode usrMsg = messages.addObject();
        usrMsg.put("role", "user");
        usrMsg.put("content", userMessage);

        ObjectNode responseFormat = requestBody.putObject("response_format");
        responseFormat.put("type", "json_object");

        return executeCall(requestBody);
    }

    private AiResponse executeCall(ObjectNode requestBody) {
        String url = "https://api.groq.com/openai/v1/chat/completions";

        try {
            log.info("Dispatching request to Groq model: {}", model);
            String responseBody = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                throw new AiPermanentException("GROQ", 500, "Groq returned empty completion content");
            }

            return new AiResponse(contentNode.asText(), "GROQ");
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            if (code == 429) {
                log.warn("Groq rate limited (429)");
                throw new AiTransientException("GROQ", 429, e.getResponseBodyAsString());
            }
            log.error("Groq client error ({}): {}", code, e.getStatusText());
            throw new AiPermanentException("GROQ", code, e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            int code = e.getStatusCode().value();
            log.warn("Groq server error ({}): {}", code, e.getStatusText());
            throw new AiTransientException("GROQ", code, e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            log.warn("Groq connection timeout / access exception: {}", e.getMessage());
            throw new AiTransientException("GROQ", 504, "Groq API request timed out or network error");
        } catch (AiTransientException | AiPermanentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Groq unexpected error: {}", e.getMessage());
            throw new AiPermanentException("GROQ", 500, e.getMessage());
        }
    }
}
