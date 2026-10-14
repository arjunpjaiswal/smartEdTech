package com.smartedtech.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;

@Component
public class GeminiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiProvider(
            @Value("${app.integrations.gemini.api-key:}") String apiKey,
            @Value("${app.integrations.gemini.model:gemini-1.5-flash}") String model,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null && !model.isBlank() ? model.trim() : "gemini-1.5-flash";
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
        return "GEMINI";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public AiResponse generateQuestionsFromPdf(byte[] pdfBytes, String prompt) {
        ObjectNode requestNode = objectMapper.createObjectNode();
        ArrayNode contents = requestNode.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");

        ObjectNode inlineData = parts.addObject().putObject("inlineData");
        inlineData.put("mimeType", "application/pdf");
        inlineData.put("data", Base64.getEncoder().encodeToString(pdfBytes));

        parts.addObject().put("text", prompt);

        ObjectNode generationConfig = requestNode.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");

        return executeCall(requestNode);
    }

    @Override
    public AiResponse generateQuestionsFromText(String text, String prompt) {
        return executeSimplePrompt(prompt + "\n\nSource Content:\n" + text);
    }

    @Override
    public AiResponse evaluateSubjective(String rubric, String questionText, String modelAnswer, String studentAnswer) {
        String prompt = String.format("""
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
        return executeSimplePrompt(prompt);
    }

    @Override
    public AiResponse generateRemediation(String prompt) {
        return executeSimplePrompt(prompt);
    }

    @Override
    public AiResponse generateChatResponse(String systemPrompt, String contextText, java.util.List<ChatHistoryItem> history, String userMessage) {
        ObjectNode requestNode = objectMapper.createObjectNode();

        String effectiveSystem = systemPrompt != null ? systemPrompt : "You are an AI Study Buddy.";
        if (contextText != null && !contextText.isBlank()) {
            effectiveSystem += "\n\nReference Study Material Content:\n" + contextText;
        }

        ObjectNode sysInstruction = requestNode.putObject("systemInstruction");
        ArrayNode sysParts = sysInstruction.putArray("parts");
        sysParts.addObject().put("text", effectiveSystem);

        ArrayNode contents = requestNode.putArray("contents");

        if (history != null) {
            for (ChatHistoryItem item : history) {
                ObjectNode turn = contents.addObject();
                turn.put("role", "assistant".equalsIgnoreCase(item.role()) ? "model" : "user");
                ArrayNode parts = turn.putArray("parts");
                parts.addObject().put("text", item.content());
            }
        }

        ObjectNode currentTurn = contents.addObject();
        currentTurn.put("role", "user");
        ArrayNode parts = currentTurn.putArray("parts");
        parts.addObject().put("text", userMessage);

        return executeCall(requestNode);
    }

    private AiResponse executeSimplePrompt(String prompt) {
        ObjectNode requestNode = objectMapper.createObjectNode();
        ArrayNode contents = requestNode.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);

        ObjectNode generationConfig = requestNode.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");

        return executeCall(requestNode);
    }

    private AiResponse executeCall(ObjectNode requestBody) {
        if (!isConfigured()) {
            throw new AiPermanentException("GEMINI", 503, "Gemini API key is not configured");
        }

        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", model, apiKey);

        try {
            log.info("Dispatching request to Gemini model: {}", model);
            String responseBody = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new AiPermanentException("GEMINI", 500, "Gemini returned empty candidate content");
            }

            return new AiResponse(textNode.asText(), "GEMINI");
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            if (code == 429) {
                log.warn("Gemini rate limited (429)");
                throw new AiTransientException("GEMINI", 429, e.getResponseBodyAsString());
            }
            log.error("Gemini client error ({}): {}", code, e.getStatusText());
            throw new AiPermanentException("GEMINI", code, e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            int code = e.getStatusCode().value();
            log.warn("Gemini server error ({}): {}", code, e.getStatusText());
            throw new AiTransientException("GEMINI", code, e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            log.warn("Gemini connection timeout / access exception: {}", e.getMessage());
            throw new AiTransientException("GEMINI", 504, "Gemini API request timed out or network error");
        } catch (AiTransientException | AiPermanentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gemini unexpected error: {}", e.getMessage());
            throw new AiPermanentException("GEMINI", 500, e.getMessage());
        }
    }
}
