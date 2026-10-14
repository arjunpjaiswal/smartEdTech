package com.smartedtech.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.common.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Component
public class CookieCsrfFilter extends OncePerRequestFilter {

    private final List<String> allowedOrigins;
    private final ObjectMapper objectMapper;

    public CookieCsrfFilter(@Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}") String allowedOriginsStr,
                            ObjectMapper objectMapper) {
        this.allowedOrigins = Arrays.stream(allowedOriginsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Enforce only on cookie-based endpoints
        if (path.endsWith("/auth/refresh") || path.endsWith("/auth/logout")) {
            String requestedWith = request.getHeader("X-Requested-With");
            if (requestedWith == null || requestedWith.isBlank()) {
                rejectCsrf(request, response, "CSRF verification failed: missing X-Requested-With header");
                return;
            }

            String origin = request.getHeader("Origin");
            if (origin == null || origin.isBlank()) {
                String referer = request.getHeader("Referer");
                if (referer != null && !referer.isBlank()) {
                    try {
                        URI refererUri = URI.create(referer);
                        origin = refererUri.getScheme() + "://" + refererUri.getAuthority();
                    } catch (Exception e) {
                        origin = null;
                    }
                }
            }

            // In dev proxy scenarios or same-origin requests, origin might match server host or be in allowed origins
            if (origin != null && !isOriginAllowed(origin, request)) {
                rejectCsrf(request, response, "CSRF verification failed: origin '" + origin + "' is not permitted");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isOriginAllowed(String origin, HttpServletRequest request) {
        if (allowedOrigins.contains(origin)) {
            return true;
        }
        // Also allow local same-origin
        String host = request.getHeader("Host");
        if (host != null) {
            String scheme = request.isSecure() ? "https://" : "http://";
            if (origin.equalsIgnoreCase(scheme + host)) {
                return true;
            }
        }
        return false;
    }

    private void rejectCsrf(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "CSRF_VERIFICATION_FAILED",
                message,
                request.getRequestURI()
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
