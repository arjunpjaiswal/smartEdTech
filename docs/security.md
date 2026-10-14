# SmartEdTech Security Architecture & Implementation

This document provides a comprehensive analysis of the security design, cryptographic choices, and defense-in-depth measures implemented across SmartEdTech.

---

## 1. Authentication Strategy

SmartEdTech uses a hybrid dual-token pattern tailored for single-page web applications to eliminate common web vulnerabilities:

### 1.1 In-Memory Access Tokens (JWT)
- **Lifespan**: 15 minutes.
- **Algorithm**: HMAC-SHA256 (signed using `JWT_SECRET`, min 256 bits / 32 bytes).
- **Client Storage**: Maintained strictly **in-memory** within the frontend application closure (`src/api/client.js`).
- **Security Rationale**: In-memory tokens are completely unreachable via Cross-Site Scripting (XSS) attacks because they are never persisted in `localStorage`, `sessionStorage`, or non-HttpOnly cookies.
- **Transport**: Sent in standard HTTP `Authorization: Bearer <token>` request headers.

### 1.2 Rotating Hashed Refresh Tokens (HttpOnly Cookie)
- **Lifespan**: 7 days.
- **Format**: Cryptographically random 32-byte opaque strings (64 hex characters generated via `SecureRandom`).
- **Storage in MySQL**: Refresh tokens are **never** stored in plaintext. Only their SHA-256 hash (`token_hash`) is persisted in the `refresh_tokens` table.
- **Cookie Attributes**:
  - `HttpOnly = true`: Inaccessible to JavaScript (XSS immune).
  - `SameSite = Lax`: Prevents ambient transmission on cross-origin requests.
  - `Path = /api/v1/auth`: Cookie is transmitted solely to authentication endpoints, never leaked on general API calls.
  - `Secure`: Enforced in non-dev environments (HTTPS only).
- **Rotation on Every Use**: Every call to `POST /api/v1/auth/refresh` revokes the incoming token and issues a fresh refresh token.
- **Token Family & Reuse Detection**: Each refresh token belongs to a persistent `family_id`. If an already-revoked refresh token is presented (indicating a potential token theft or replay attack), the backend instantly invalidates the entire token family, forcing immediate re-authentication.

---

## 2. CSRF Defense for Cookie-Based Endpoints

Because bearer token requests are immune to standard Cross-Site Request Forgery (browsers do not attach arbitrary `Authorization: Bearer` headers cross-origin), CSRF protection focuses on cookie-authenticated endpoints:
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

### 2.1 Defense-in-Depth Mechanisms (`CookieCsrfFilter`)
1. **Custom Header Requirement**: The client must supply `X-Requested-With: XMLHttpRequest` (or standard pre-flight triggering headers). Cross-origin simple requests (e.g. form submissions, image pings) cannot set custom headers without passing a CORS pre-flight (`OPTIONS`) check.
2. **Origin & Referer Validation**: The filter strictly verifies the request's `Origin` against the whitelist defined in `APP_CORS_ALLOWED_ORIGINS` (or the host origin). Any unwhitelisted origin is immediately rejected with HTTP `403 Forbidden`.

---

## 3. Password Hashing & Account Security

- **Password Storage**: Passwords are encrypted using BCrypt with Spring Security's `BCryptPasswordEncoder(cost = 12)`. Plaintext passwords are never logged or stored.
- **Account State & Deactivation**: Users have an `enabled` flag. Deactivated users are instantly rejected across:
  - Login attempts (`401 Bad credentials` / `403 Account deactivated`).
  - Refresh token exchanges (`401 Unauthorized`).
  - Active JWT access tokens (`JwtAuthenticationFilter` validates user status on every request).
  - WebSocket connections (`WebSocketAuthChannelInterceptor` rejects connection).

---

## 4. Rate Limiting (Bucket4j)

Token-bucket rate limiting protects sensitive endpoints against brute-force attacks and denial-of-service:
- **Authentication Endpoints** (`/login`, `/register`, `/refresh`): 10 requests per minute per IP address.
- **AI Endpoints** (`/ai/generate-questions`, `/chat/conversations/*/messages`): 15 requests per minute per user/IP.
- **STOMP WebSocket Chat Messages**: 30 messages per minute per user.
- **Exceeded Limit Response**: HTTP `429 Too Many Requests` with a descriptive error payload.

---

## 5. File Storage, Upload Validation & Path Traversal Prevention

- **Interface Abstraction**: `FileStorage` interface isolates storage operations (`store`, `loadAsResource`, `delete`) behind safe implementations.
- **Safe Server-Generated Filenames**: The original client filename is sanitized and discarded; files are saved on disk as `{UUID}{safe_extension}`.
- **Path Traversal Guards**:
  - `destinationFile.getParent().equals(this.rootLocation)` prevents directory traversal on upload.
  - `file.startsWith(this.rootLocation)` prevents arbitrary file read via `../` sequences on download.
- **Strict Content-Type & Magic Byte Validation** (`FileTypeValidator`):
  - Enforces 20MB file size ceiling.
  - Verifies file magic numbers (e.g. `%PDF-` for PDFs, `PK\x03\x04` for DOCX/PPTX, `\xD0\xCF\x11\xE0` for DOC/PPT, null-byte scanning for text files). Renamed executables (`.exe` renamed to `.pdf`) are rejected with `400 Bad Request`.

---

## 6. Real-Time STOMP WebSocket Security

SmartEdTech's WebSocket broker (`/ws`) enforces strict security at the channel interceptor level (`WebSocketAuthChannelInterceptor`):
1. **CONNECT Authentication**: Requires a valid, unexpired Bearer JWT in the STOMP connection headers (`Authorization` or `X-Authorization`).
2. **SUBSCRIBE Authorization**:
   - `/topic/sessions/{id}/polls`: Verified against active session and classroom membership (enrolled student or classroom teacher).
   - `/topic/sessions/{id}/polls/teacher`: Strictly restricted to the classroom teacher or ADMIN.
   - Arbitrary destination hijacking is rejected with `AccessDeniedException`.
3. **SEND Integrity**: The sender identity is always derived from the authenticated principal in the security context, completely ignoring any sender metadata in the client payload.

---

## 7. Assessment Privacy & Answer Key Protection

To maintain academic integrity:
- **Zero Answer Key Leakage**: The backend `toQuestionDto` method conditionally filters out correct answers:
  ```java
  boolean isStudent = (user.getRole() == Role.STUDENT);
  // correct flag, model answers, and rubrics are set to null for student callers
  opt.isCorrect() -> isStudent ? null : opt.isCorrect();
  q.getModelAnswer() -> isStudent ? null : q.getModelAnswer();
  q.getRubric() -> isStudent ? null : q.getRubric();
  ```
- **Lifecycle Publishing State**: Students cannot view assessments, questions, or attempts until an assessment is published by the teacher.
- **Attempt Isolation**: Students cannot inspect other students' attempts or evaluations. Teachers can only view attempts for classrooms they instruct.

---

## 8. Fail-Fast Configuration & Integration Status Reporting

- **Mandatory Configuration**: The application refuses to start if `SPRING_DATASOURCE_*` or `JWT_SECRET` (minimum 32 characters) are missing.
- **Optional Integrations** (Google Gemini, Groq, Jitsi as a Service):
  - Missing keys do not crash the app on startup.
  - `GET /api/v1/system/integrations` reports `CONFIGURED` or `NOT_CONFIGURED` without exposing credentials.
  - Attempting to use an unconfigured service immediately returns HTTP `503 INTEGRATION_NOT_CONFIGURED` with a message identifying the missing environment variable. Production code never falls back to fake or mock data.
