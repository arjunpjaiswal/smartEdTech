# SmartEdTech: Project Rules (read this at the start of EVERY session)

Save this file in the repo root as `AGENTS.md` (or `CLAUDE.md` / `.cursor/rules`, depending on your tool).
Phase prompts are in `PHASE_PROMPTS.md`. Do one phase per session.

---

## 0. What we are building

SmartEdTech is a working AI-powered classroom platform: live video classes (Jitsi), real-time polls and chat, AI quiz generation from PDFs, AI grading, weakness analytics, and an AI study buddy. Three roles: ADMIN, TEACHER, STUDENT.

This is NOT a demo with static pages. Every screen must talk to the real backend, and the backend must talk to the real MySQL database and the real AI APIs.

---

## 1. THE MOST IMPORTANT RULE: NO FAKE OUTPUT, NO STATIC FALLBACKS

Past agents on this project hid failures by generating fake data that looked like the real output. That is the worst possible behavior, because it makes broken code look finished. Follow these rules strictly:

1. **Never** return hardcoded, sample, mocked, canned, "demo", or placeholder data from any production code path (backend or frontend) to stand in for a real result.
2. **Never** write a `catch` block (Java or JS) that swallows an error and returns fake or default content. Errors must propagate as real errors: a proper HTTP status plus the error body described in section 6 on the backend, and a visible error state with a Retry button on the frontend.
3. **Never** write `if (apiKey == null) return sampleQuestions()` or anything similar. If a key or integration is missing, the feature returns `503 INTEGRATION_NOT_CONFIGURED` with a message naming the missing environment variable. The UI shows that message.
4. **Never** keep local arrays or constants in React that pretend to be API data. If the API call fails, show the error. Do not fall back to sample data.
5. **Never** simulate AI output, grading, video rooms, WebSocket messages, file uploads, or analytics.
6. Mocks, stubs, and fakes are allowed ONLY inside `backend/src/test/**` and `frontend/src/**/*.test.*`. They must never be imported by production code.
7. Seed/demo data is allowed ONLY as real rows in MySQL, created by a dev-profile seeder (`@Profile("dev")`) or a clearly named Flyway dev migration, and always read back through the real APIs.
8. The ONLY allowed "fallback" is **provider failover for AI calls**: if Gemini fails with a transient error (HTTP 429, 5xx, or timeout), retry the same real request on Groq (or the reverse). It must be a real API call, and the response must record which provider produced it. If both fail, return the real error. Do not fail over on 400/401/403 or on invalid output: those are bugs or config errors and must surface.
9. **When something does not work:** stop. Read the actual error. Find the root cause. Fix it properly. If you genuinely cannot fix it (for example, it needs a credential you do not have), mark it `BLOCKED` in `PROJECT_STATUS.md` with the exact error and what is needed, then move on to something unrelated. Do NOT work around it by faking the result.
10. Do not write "TODO", "for now", "temporary", "mock", "dummy", "sample", or "placeholder" in production code. If a feature is incomplete, it is not marked done.

### Self-audit (mandatory at the end of every phase)
Run these and justify or remove every hit in production code (hits in test code are fine):

```
grep -rniE "mock|fake|dummy|sample|placeholder|hardcod|lorem|fallback|simulate|TODO|FIXME" backend/src/main frontend/src
```
Also search for empty or silent catch blocks (`catch (Exception e) {}`, `.catch(() => [])`, `.catch(() => ({}))`) and for any array literal of objects in React pages. Report what you found and what you did about it in `PROJECT_STATUS.md`.

---

## 2. Honesty and verification rules

- A feature is only "done" when you have **run it and seen it work**. Evidence = command + real output (a curl against the running server, a test run, a Flyway log, a screenshot-free description of what the UI call returned).
- Use exactly these statuses in `PROJECT_STATUS.md`:
  - `VERIFIED`: ran it, it works, evidence recorded.
  - `IMPLEMENTED, NEEDS CREDENTIALS`: code is complete and tested up to the external call, but needs a real Gemini/Groq/Jitsi credential to verify end to end.
  - `BLOCKED`: cannot proceed. Include exact error and what is needed.
  - `NOT STARTED`
- Never mark something `VERIFIED` because it compiles. Compiling is not working.
- If a test fails, fix the code or the test for a real reason. Never delete or weaken a test just to make it pass, and never add `@Disabled` to hide a failure.
- If you are unsure how a library or API behaves, read its official documentation or source. Do not guess method names or model names.
- Do not claim the study buddy uses RAG. It does not.

---

## 3. Tech stack (fixed)

**Frontend:** React (latest stable), Vite, JavaScript (not TypeScript), Tailwind CSS (latest stable), React Router, Axios, Lucide React, Recharts, React Hook Form + Zod, Jitsi iframe/External API.

**Backend:** Java 21, Spring Boot 3.x (newest supported 3.x patch; record the exact version in `PROJECT_STATUS.md`; if no 3.x line is still supported, stop and ask), **Gradle** (Groovy DSL, `build.gradle` + `settings.gradle`, always use the Gradle wrapper `./gradlew`, never a globally installed `gradle`; use the Spring Boot Gradle plugin with the dependency-management plugin so Spring-managed versions are not mixed by hand), Spring Web, Spring Security, Spring Data JPA/Hibernate, Validation, WebSocket (STOMP), Actuator, Flyway, springdoc-openapi, JJWT (or Nimbus), Apache PDFBox, Bucket4j (rate limiting), JUnit 5, Mockito.

**Database:** MySQL 8+, InnoDB, Flyway migrations only (`spring.jpa.hibernate.ddl-auto=validate`, never `update`/`create`).

**AI:** Google Gemini API (native PDF input + generation) and Groq API (text only, so it never receives PDFs, only PDFBox-extracted text). Model names come from env vars `GEMINI_MODEL` and `GROQ_MODEL`. Check each provider's current official docs for valid model IDs before choosing defaults, and do not hardcode a model name anywhere except the documented default in `application.yml`.

**Build tool:** Gradle only. Do not create a `pom.xml` and do not use Maven. Standard commands: `./gradlew bootRun`, `./gradlew test`, `./gradlew build`, `./gradlew dependencies` (use `./gradlew test --tests "*ClassName*"` for a single test class). Run them from the `backend/` folder. Add `org.flywaydb:flyway-mysql` along with `flyway-core`, since Flyway 10+ needs the MySQL module separately. Set the Java toolchain to 21 in `build.gradle`. Commit `gradlew`, `gradlew.bat`, and `gradle/wrapper/`, but not `.gradle/` or `build/` (add these to `.gitignore`).

**Forbidden:** Firebase/Firestore (any part), MongoDB, Node/Express backend, Python backend, H2 for tests, microservices, Maven.

No Docker is required to run the app. (Tests use a local MySQL test database; see section 8.)

---

## 4. Architecture

Monolith. Two folders:

```
smart-edtech/
├── frontend/
├── backend/
├── docs/
├── AGENTS.md
├── PHASE_PROMPTS.md
├── PROJECT_STATUS.md
├── .env.example
└── README.md
```

Backend packages under `com.smartedtech`: `config, security, auth, user, classroom, session, attendance, poll, discussion, material, assessment, evaluation, analytics, chatbot, notification, ai, common`.

Each module: controller (thin) → service (business rules + authorization checks) → repository. DTOs for all API input/output; never expose JPA entities or password hashes.

AI layer: an `AiProvider` interface with `GeminiProvider` and `GroqProvider`, and a service that handles timeouts, retries on transient errors, JSON validation, and failover as described in section 1, rule 8.

Local file storage behind a `FileStorage` interface (so object storage can replace it later). Safe generated filenames, no client-supplied paths, no path traversal, no internal paths in responses.

---

## 5. Decisions already made (do not re-debate)

**Authentication**
- Passwords: BCrypt.
- Access token: JWT, 15 minutes, sent in the `Authorization: Bearer` header, kept **in memory only** in React (never localStorage/sessionStorage).
- Refresh token: opaque random value, **HttpOnly, SameSite=Lax, Secure in prod, path `/api/v1/auth`**, 7 days, rotated on every refresh, stored **hashed** in MySQL, revocable. Reuse of a rotated token revokes the whole token family.
- On page reload the frontend calls `/auth/refresh` to restore the session.
- CSRF: the Bearer-header endpoints do not need CSRF tokens. The cookie-based endpoints (`/auth/refresh`, `/auth/logout`) must require a custom header (e.g. `X-Requested-With: XMLHttpRequest`) AND an allowed `Origin`, so cross-site requests are rejected. Do not disable CSRF protection without this in place; document the reasoning in `docs/security.md`.
- Dev: Vite proxies `/api` and `/ws` to `http://localhost:8080`, so the browser sees one origin. CORS is still configured from `APP_CORS_ALLOWED_ORIGINS` for deployed setups.
- Public registration allows only STUDENT or TEACHER. The first ADMIN is created at startup from `APP_BOOTSTRAP_ADMIN_EMAIL` / `APP_BOOTSTRAP_ADMIN_PASSWORD` env vars, only if no admin exists, and the app refuses to start with a weak or missing password in non-dev profiles.
- Rate limiting (Bucket4j): login, register, refresh, and all AI endpoints.

**Jitsi**
- Use **JaaS (8x8 Jitsi as a Service)** with RS256 JWTs signed by the backend. Env vars: `JITSI_APP_ID`, `JITSI_KID`, `JITSI_PRIVATE_KEY_PATH`.
- The backend `/sessions/{id}/join` verifies the user may join, then returns a short-lived Jitsi JWT plus the room name. The private key never reaches the browser.
- Teachers get the moderator flag; students do not.
- If Jitsi env vars are missing, `/join` returns `503 INTEGRATION_NOT_CONFIGURED` and the UI says so. No fake video panel, no public-room fallback.
- Document in the README how to get JaaS credentials, and how to point to a self-hosted Jitsi instead.
- Attendance is recorded by the backend when a join token is issued and updated from client join/leave events sent to the backend. Note in the docs that this is client-reported.

**AI generation is asynchronous**
- `POST /ai/generate-questions` creates a `GenerationJob` row (PENDING → RUNNING → SUCCEEDED/FAILED), runs on a bounded executor, and returns `202` with the job id. The frontend polls `GET /ai/jobs/{id}`. Failed jobs store a real error code and message and can be retried.
- Grading of subjective answers uses the same job mechanism.

**Real-time**
- STOMP over WebSocket (`/ws`). A channel interceptor authenticates the JWT on CONNECT and authorizes every SUBSCRIBE (session membership checks). The sender identity always comes from the authenticated principal, never from the message body.
- Single in-memory broker is acceptable for now. Note in the docs that multi-instance needs an external broker.

**Notifications**
- A `Notification` table (user, type, title, body, read flag, created_at) with REST endpoints, shown in the top bar. Created when sessions are scheduled, assessments are published, results are released, and so on.

**Scores:** `DECIMAL(6,2)`. AI-suggested marks are stored as provisional (`AI_SUGGESTED`) until a teacher accepts or edits them.

---

## 6. API conventions

- Base path `/api/v1`. springdoc OpenAPI at `/swagger-ui` (dev profile only).
- Pagination on all list endpoints (`page`, `size`, `sort`).
- Every error returns this JSON shape from a `@RestControllerAdvice`:
  `{ "timestamp", "status", "code", "message", "path", "fieldErrors": [] }`
  with proper codes: 400 validation, 401 unauthenticated, 403 forbidden, 404, 409 conflict (duplicate enrollment/vote/attempt), 429 rate-limited, 502 upstream AI failure, 503 `INTEGRATION_NOT_CONFIGURED`.
- Authorization is enforced in the **backend** (method security + service checks, e.g. a teacher can only touch their own classes, a student only their enrolled classes). Hiding buttons in React is not security.
- Students never receive answer keys, model answers, or other students' data before they are allowed to.

---

## 7. Configuration

- `application.yml` uses env vars only; no secrets in the repo. Provide `.env.example` with every variable:
  `SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, JWT_SECRET (min 32 bytes), APP_CORS_ALLOWED_ORIGINS, APP_FILE_STORAGE_PATH, GEMINI_API_KEY, GEMINI_MODEL, GROQ_API_KEY, GROQ_MODEL, JITSI_APP_ID, JITSI_KID, JITSI_PRIVATE_KEY_PATH, APP_BOOTSTRAP_ADMIN_EMAIL, APP_BOOTSTRAP_ADMIN_PASSWORD`
- **Required config** (DB, JWT secret): fail fast on startup with a clear message naming what is missing.
- **Optional integrations** (Gemini, Groq, Jitsi): the app starts, and `GET /api/v1/system/integrations` (admin/teacher) reports for each one `CONFIGURED` or `NOT_CONFIGURED` (without revealing keys). Features using a missing integration return 503 as described above.
- Only the API base URL (or nothing, thanks to the proxy) goes in Vite env. No secrets in the frontend, ever.

---

## 8. Testing

- JUnit 5 + Mockito + Spring Boot Test. Integration tests run against a **real MySQL test schema** (`smartedtech_test`, configured via env/`application-test.yml`). Not H2.
- Required coverage: register/login, BCrypt, JWT validation and expiry, refresh rotation and reuse detection, role authorization (403/401 cases), classroom + enrollment (including duplicate), material access control, assessment publish rules, objective grading, attempt limits and deadline checks, duplicate poll vote (DB constraint), analytics queries, AI provider failure handling (using test doubles **only in test code**), global exception handler.
- Frontend: `npm run build` must pass, plus tests for login/register flow, protected routes, API error display, and the assessment-taking screen (Vitest + React Testing Library with the API layer mocked **in tests only**).
- Run the real commands and paste the real results into `PROJECT_STATUS.md`. Never report a test result you did not actually see.

---

## 8b. UI direction

Clean light theme, navy/slate text, indigo primary, rounded cards, restrained shadows, Lucide icons (no emoji as icons), skeleton loaders, empty states for new accounts, error states with Retry, responsive sidebar + top bar with role-specific navigation. All numbers on dashboards come from backend APIs. Brand-new accounts must show proper empty states, not fake numbers.

---

## 9. Working style

1. Inspect the workspace and tools before writing anything.
2. Keep the build working after every change. Run the build/tests before moving on.
3. Make normal technical decisions yourself. Ask me only when a decision is blocked by something outside the repo (credentials, missing tools, a conflict between these rules).
4. Do one phase per session. At the end of the phase: run the self-audit (section 1), update `PROJECT_STATUS.md`, and **stop**. Do not start the next phase.
5. `PROJECT_STATUS.md` format: per module: status (from section 2), evidence, known issues, exact next steps. Keep it short and factual.
6. Plain, direct language in code comments, docs, and the README. No marketing tone.
