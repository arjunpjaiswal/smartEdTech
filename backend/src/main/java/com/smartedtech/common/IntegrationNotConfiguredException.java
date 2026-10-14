package com.smartedtech.common;

import org.springframework.http.HttpStatus;

public class IntegrationNotConfiguredException extends ApiException {
    public IntegrationNotConfiguredException(String envVarName) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_NOT_CONFIGURED",
                "Integration not configured: missing required environment variable " + envVarName);
    }
}
