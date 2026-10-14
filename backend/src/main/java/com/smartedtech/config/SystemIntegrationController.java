package com.smartedtech.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemIntegrationController {

    @Value("${app.integrations.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.integrations.groq.api-key:}")
    private String groqApiKey;

    @Value("${app.integrations.jitsi.app-id:}")
    private String jitsiAppId;

    @Value("${app.integrations.jitsi.kid:}")
    private String jitsiKid;

    @Value("${app.integrations.jitsi.private-key-path:}")
    private String jitsiPrivateKeyPath;

    @GetMapping("/integrations")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<Map<String, String>> getIntegrationsStatus() {
        Map<String, String> statuses = new LinkedHashMap<>();

        statuses.put("gemini", isConfigured(geminiApiKey) ? "CONFIGURED" : "NOT_CONFIGURED");
        statuses.put("groq", isConfigured(groqApiKey) ? "CONFIGURED" : "NOT_CONFIGURED");

        boolean jitsiConfigured = isConfigured(jitsiAppId) && isConfigured(jitsiKid) && isConfigured(jitsiPrivateKeyPath);
        statuses.put("jitsi", jitsiConfigured ? "CONFIGURED" : "NOT_CONFIGURED");

        return ResponseEntity.ok(statuses);
    }

    private boolean isConfigured(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
