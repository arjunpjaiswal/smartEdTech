# SmartEdTech: Project Status

Current Status: Verification & Hardening Pass - COMPLETED
Backend: Spring Boot 3.3.5, Java 21, Gradle 8.12.1 wrapper (Groovy DSL)
Database: MySQL 8.0 (Flyway migrations V1 through V5, `spring.jpa.hibernate.ddl-auto=validate`)
Frontend: React 18, Vite 5, Tailwind CSS 3, React Router 6, Axios, @stomp/stompjs

---

## Retraction of Earlier Inaccurate Claims

1. **Premature Phase 5 Sign-off**: Phase 5 was previously marked completed despite `ChatbotService` having syntax and compilation errors. This has been remediated; `ChatbotService` and `ChatbotController` are fully compiled, verified against MySQL, and covered with real multi-turn persistence, tenant isolation, and failure handling integration tests (`StudyBuddyIntegrationTest`).
2. **Incomplete Prior Fresh-Clone Test**: The earlier status report claimed "fresh-clone VERIFIED" before executing a true fresh database lifecycle. A genuine fresh-start verification was conducted in Task 7: dropping the `smartedtech` database, creating the dedicated `smartedtech_user` via `docs/mysql-setup.sql`, booting the backend using strictly README-documented environment variables, and verifying all Flyway migrations (`V1` through `V5`) applied with `success = 1`.
3. **Flawed Single-Line Audit**: The previous self-audit used single-line scans that missed multi-line empty catches and swallowed catches in React components and `LocalFileStorage.delete()`. A multi-line aware audit was performed, and all instances were remediated with real error states, retry triggers, and logger warnings.
4. **Vulnerability Claims in Production Bundles**: The previous report claimed zero vulnerabilities affect runtime production bundles. That was false: `react-router-dom` and `react-router` are direct/transitive runtime production dependencies and carry known advisories (GHSA-wrjc-x8rr-h8h6 and GHSA-337j-9hxr-rhxg). A full, transparent audit report is detailed below.

---

## Module Status Overview

| Module | Status | Verification Evidence Summary |
|---|---|---|
| **1. Authentication & Security** | `VERIFIED` | 12 tests passed across `AuthIntegrationTest` (11) and `FullCookieAuthFlowIntegrationTest` (1). BCrypt password hashing, in-memory JWT (15m), rotating HttpOnly refresh cookie (7d, SameSite=Lax, Path=/api/v1/auth), token family reuse revocation, CSRF header checks (`X-Requested-With` + Origin), and Bucket4j rate limiting. |
| **2. Admin Bootstrap & Deactivation** | `VERIFIED` | 9 tests passed across `AdminBootstrapServiceTest` (5) and `AdminAndDeactivationIntegrationTest` (4). Non-dev startup refuses to boot unless `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD` (min 12 chars) are set; dev profile falls back to documented defaults. Deactivated users blocked across login, refresh, access tokens, and WebSocket STOMP. |
| **3. System Integrations Reporting** | `VERIFIED` | `GET /api/v1/system/integrations` reports `CONFIGURED` / `NOT_CONFIGURED` without leaking keys. Access restricted to TEACHER and ADMIN; Students receive 403 Forbidden. Tested in `AuthorizationMatrixIntegrationTest`. |
| **4. Frontend App Shell & Navigation** | `VERIFIED` | 13/13 Vitest tests pass; `npm run build` succeeds cleanly in 16.88s. In-memory token management, session restoration via `/auth/refresh`, role-based route guards, and zero fake data. |
| **5. Classroom & Enrollment** | `VERIFIED` | 6/6 tests passed in `ClassroomIntegrationTest`. Unique join code generation, enrollment capacity limits, duplicate enrollment prevention (409 Conflict via DB unique constraint), and teacher student management. |
| **6. Live Sessions & Jitsi Video** | `IMPLEMENTED, NEEDS CREDENTIALS` (live JaaS video) / `VERIFIED` (scheduling & attendance) | 5/5 tests in `SessionIntegrationTest` + `AuthorizationMatrixIntegrationTest`. Unconfigured Jitsi credentials return HTTP 503 `INTEGRATION_NOT_CONFIGURED`. Configured backend signs RS256 JWTs. MySQL attendance tracking and heartbeats. |
| **7. Real-Time Polls & STOMP Discussion** | `VERIFIED` | 10 tests passed across `PollIntegrationTest` (4), `WebSocketAndDiscussionIntegrationTest` (5), and `RealTwoClientsStompIntegrationTest` (1). STOMP CONNECT JWT authentication, SUBSCRIBE session authorization, principal-derived sender identity, and duplicate vote rejection (409 Conflict with DB constraint). |
| **8. Study Materials & File Storage** | `VERIFIED` | 7 tests passed across `MaterialIntegrationTest` (4), `PathTraversalIntegrationTest` (2), and `StorageFailurePathLeakIntegrationTest` (1). Secure UUID storage, magic byte format verification (`%PDF-`, `PK\x03\x04`, `\xD0\xCF\x11\xE0`), path traversal resistance, and generic internal error shielding (no paths leaked to clients). DB row deleted before file on disk. |
| **9. Notifications** | `VERIFIED` | 2/2 tests in `NotificationIntegrationTest`. Automatic notification creation on session scheduled, material uploaded, student enrolled. Scoped strictly by user principal; unread count badge, mark-as-read, and mark-all-as-read endpoints verified against MySQL. |
| **10. Dashboards** | `VERIFIED` | Tested in `DashboardAndAnalyticsIntegrationTest`. Role-scoped stats (`stat1..4`), upcoming sessions, and recent materials computed directly from MySQL for TEACHER, STUDENT, and ADMIN with exact expected counts. Clean empty states for brand-new accounts. |
| **11. Assessments & AI Quiz Generation** | `IMPLEMENTED, NEEDS CREDENTIALS` (live AI) / `VERIFIED` (authoring & grading) | 18 tests passed across `AssessmentIntegrationTest` (7), `QuestionValidationTest` (7), `StudentAnswerKeyLeakIntegrationTest` (1), and `MissingIntegrations503IntegrationTest` (3). Answer keys, model answers, and rubrics are stripped for students before result release. Unconfigured AI returns HTTP 503 fail-fast. |
| **12. Subjective AI Evaluation & Grading** | `IMPLEMENTED, NEEDS CREDENTIALS` (live AI) / `VERIFIED` (evaluation flow & overrides) | 5/5 tests in `SubjectiveEvaluationAndRemediationIntegrationTest`. Asynchronous evaluation job parsing, score clamping (0 to maxMarks), provisional `AI_SUGGESTED` persistence, teacher override/accept (`PUT /api/v1/evaluations/{id}`), score range validation (400 Bad Request on out-of-range), and malformed JSON job error handling. |
| **13. Weakness Analytics & Remediation** | `IMPLEMENTED, NEEDS CREDENTIALS` (live AI remediation) / `VERIFIED` (analytics & access control) | Tested in `DashboardAndAnalyticsIntegrationTest` and `SubjectiveEvaluationAndRemediationIntegrationTest`. Incorrect MCQ answers generate `WeakConcept` rows. Endpoint `GET /api/v1/students/{id}/weak-concepts` verified with exact expected counts and tenant isolation. Remediation plan generation and student/teacher access control verified. |
| **14. AI Study Buddy Chatbot** | `IMPLEMENTED, NEEDS CREDENTIALS` (live AI) / `VERIFIED` (chat flow & persistence) | 4/4 tests in `StudyBuddyIntegrationTest`. Multi-turn chat persistence in MySQL (`chat_conversations` and `chat_messages`), strict student conversation isolation (404 on cross-student access), fail-fast HTTP 503 on missing AI keys, and proper HTTP 502 `UPSTREAM_AI_ERROR` on provider failure. |
| **15. API Authorization Matrix** | `VERIFIED` | 10/10 tests passed in `AuthorizationMatrixIntegrationTest`. Cross-tenant wrong-role and wrong-owner rejection verified across all endpoints. Documented in `docs/authorization-matrix.md`. |
| **16. Test Infrastructure Safety** | `VERIFIED` | 2/2 tests passed in `TestDatabaseCleanerGuardTest`. `TestDatabaseCleaner` strictly refuses execution unless active datasource schema name ends with `_test`. |

---

## Test Suite Execution Evidence

### Backend Tests (`.\gradlew.bat test`)
Executed against real MySQL test database `smartedtech_test`:
- `com.smartedtech.ai.AiServiceTest`: 3 passed, 0 failed
- `com.smartedtech.ai.MissingIntegrations503IntegrationTest`: 3 passed, 0 failed
- `com.smartedtech.analytics.DashboardAndAnalyticsIntegrationTest`: 4 passed, 0 failed
- `com.smartedtech.assessment.AssessmentIntegrationTest`: 7 passed, 0 failed
- `com.smartedtech.assessment.QuestionValidationTest`: 7 passed, 0 failed
- `com.smartedtech.assessment.SubjectiveEvaluationAndRemediationIntegrationTest`: 5 passed, 0 failed
- `com.smartedtech.auth.AuthIntegrationTest`: 11 passed, 0 failed
- `com.smartedtech.auth.FullCookieAuthFlowIntegrationTest`: 1 passed, 0 failed
- `com.smartedtech.chatbot.StudyBuddyIntegrationTest`: 4 passed, 0 failed
- `com.smartedtech.classroom.ClassroomIntegrationTest`: 6 passed, 0 failed
- `com.smartedtech.config.AdminBootstrapServiceTest`: 5 passed, 0 failed
- `com.smartedtech.discussion.RealTwoClientsStompIntegrationTest`: 1 passed, 0 failed
- `com.smartedtech.discussion.WebSocketAndDiscussionIntegrationTest`: 5 passed, 0 failed
- `com.smartedtech.material.MaterialIntegrationTest`: 4 passed, 0 failed
- `com.smartedtech.material.PathTraversalIntegrationTest`: 2 passed, 0 failed
- `com.smartedtech.material.StorageFailurePathLeakIntegrationTest`: 1 passed, 0 failed
- `com.smartedtech.notification.NotificationIntegrationTest`: 2 passed, 0 failed
- `com.smartedtech.poll.PollIntegrationTest`: 4 passed, 0 failed
- `com.smartedtech.security.AuthorizationMatrixIntegrationTest`: 10 passed, 0 failed
- `com.smartedtech.security.StudentAnswerKeyLeakIntegrationTest`: 1 passed, 0 failed
- `com.smartedtech.session.SessionIntegrationTest`: 5 passed, 0 failed
- `com.smartedtech.TestDatabaseCleanerGuardTest`: 2 passed, 0 failed
- `com.smartedtech.user.AdminAndDeactivationIntegrationTest`: 4 passed, 0 failed
- **Total: 97 tests completed, 97 passed, 0 failed, 0 ignored (Success rate: 100%)**

### Frontend Tests (`npm test -- --run`)
Executed with Vitest and React Testing Library:
- `src/test/auth.test.jsx`: 4 passed, 0 failed
- `src/test/classroom.test.jsx`: 3 passed, 0 failed
- `src/test/assessment.test.jsx`: 3 passed, 0 failed
- `src/test/realtime.test.jsx`: 3 passed, 0 failed
- **Total: 13 tests completed, 13 passed, 0 failed (Success rate: 100%)**

### Frontend Production Build (`npm run build`)
- Vite production build completed cleanly in 16.88s.
- Emitted: `dist/index.html` (0.55 kB), `dist/assets/index-XpUmpq3L.css` (36.56 kB), `dist/assets/index-O1Q4hQ1M.js` (515.27 kB).

---

## Dependency & Vulnerability Analysis

### Spring Boot 3.x Support Status (Queried via Official APIs)
- Queried `https://start.spring.io/metadata/client`: Spring Initializr active generation targets are Spring Boot 4.0.8, 4.1.1, and 4.2.0-M2. Spring Boot 3 has entered commercial support.
- Queried Maven Central (`org.springframework.boot:spring-boot` metadata) & GitHub Releases:
  - Newest released 3.x patch: **`3.5.16`** (released June 25, 2026).
  - Current project version: **`3.3.5`**.
  - Upgrade implications: Upgrading from 3.3.5 to 3.5.16 involves Spring Security 6.x minor changes, Hibernate 6.x version bumps, and Jackson upgrades. Requires verifying `@EnableMethodSecurity`, Flyway MySQL connector compatibility, and test suite execution.

### Frontend Dependencies (`npm outdated`)
- `react` / `react-dom`: Current `18.3.1`, Wanted `18.3.1`, Latest `19.3.0`
- `vite`: Current `5.4.21`, Wanted `5.4.21`, Latest `8.3.4`
- `react-router-dom`: Current `6.30.6`, Wanted `6.30.6`, Latest `7.18.4`
- `tailwindcss`: Current `3.4.19`, Wanted `3.4.19`, Latest `4.3.3`
- `vitest`: Current `2.1.9`, Wanted `2.1.9`, Latest `5.0.4`
- `@hookform/resolvers`: Current `3.10.0`, Wanted `3.10.0`, Latest `5.9.1`
- `recharts`: Current `2.15.4`, Wanted `2.15.4`, Latest `3.10.1`

### Frontend Vulnerability Audit (`npm audit`)
Total vulnerabilities reported: **15** (7 moderate, 6 high, 2 critical).
Running `npm audit fix` (without `--force`) resolves **0** vulnerabilities, because every fix requires a breaking major version bump.

Individual breakdown:
1. **`react-router-dom`** (Moderate) - **Runtime Production Dependency** (Direct in `package.json`).
   - Advisories: GHSA-wrjc-x8rr-h8h6 (Open redirect via backslash in `<Link>` / `useNavigate`), GHSA-337j-9hxr-rhxg (Constructor Injection in SSR hydration).
   - Fix requires: `react-router-dom@7.18.4` (breaking semver major).
2. **`react-router`** (Moderate) - **Runtime Production Dependency** (Transitive via `react-router-dom`).
   - Advisories: Same as above.
3. **`tailwindcss`** (High) - **Dev-Only Dependency** (`devDependencies`).
   - Vulnerable via transitive build dependencies: `chokidar`, `fast-glob`, `micromatch`, `postcss-nested`, `postcss-selector-parser`.
   - Fix requires: `tailwindcss@4.3.3` (breaking major).
4. **`braces`** (High) - **Dev-Only Transitive Dependency** (via `chokidar` / `micromatch` in `tailwindcss`).
   - Advisory: GHSA-vfj7-8cjw-p6xm (Stack-exhaustion DoS in nested patterns).
5. **`chokidar`** (High) - **Dev-Only Transitive Dependency** (via `tailwindcss`).
6. **`fast-glob`** (High) - **Dev-Only Transitive Dependency** (via `tailwindcss`).
7. **`micromatch`** (High) - **Dev-Only Transitive Dependency** (via `tailwindcss`).
8. **`postcss-nested`** (Moderate) - **Dev-Only Transitive Dependency** (via `tailwindcss`).
9. **`postcss-selector-parser`** (Moderate) - **Dev-Only Transitive Dependency** (via `tailwindcss`).
   - Advisory: GHSA-rj75-hqrm-r3gf (Flat selector parsing CPU exhaustion).
10. **`vite`** (High) - **Dev-Only Dependency** (`devDependencies`).
    - Advisories: GHSA-4w7w-66w2-5vf9 (Path traversal in sourcemaps), GHSA-v6wh-96g9-6wx3 (NTLMv2 hash disclosure on Windows via `launch-editor`), GHSA-fx2h-pf6j-xcff (`server.fs.deny` bypass on Windows).
    - Fix requires: `vite@8.3.4` (breaking major).
11. **`esbuild`** (Moderate) - **Dev-Only Transitive Dependency** (via `vite`).
    - Advisory: GHSA-67mh-4wv8-2f99 (Dev server cross-origin requests).
12. **`vitest`** (Critical) - **Dev-Only Dependency** (`devDependencies`).
    - Advisories: GHSA-5xrq-8626-4rwp (Vitest UI arbitrary file read/execution), GHSA-82fw-gwwq-j7x9 (Path traversal via `@vitest/mocker`).
    - Fix requires: `vitest@5.0.4` (breaking major).
13. **`@vitest/mocker`** (Moderate) - **Dev-Only Transitive Dependency** (via `vitest`).
14. **`tinypool`** (Critical) - **Dev-Only Transitive Dependency** (via `vitest`).
    - Advisories: GHSA-5gmw-xhrv-c9v3, GHSA-85c8-ppgw-ccpr (Prototype pollution leading to RCE in worker options).
15. **`vite-node`** (Moderate) - **Dev-Only Transitive Dependency** (via `vitest`).

---

## Multi-Line Aware Self-Audit Results

### 1. Swallowed / Silent Catch Blocks
- **`backend/.../LocalFileStorage.java`**: Swallowed `catch (IOException ignored) {}` in file deletion replaced with SLF4J `log.warn("Failed to delete stored file: {}", filename, e)` with best-effort cleanup documentation.
- **`backend/.../MaterialService.java`**: Deletion ordering corrected to delete MySQL database row first, flush to database, and only then delete physical file from storage.
- **`frontend/.../AiGeneratorModal.jsx`**: Swallowed catch on job polling replaced with proper error state banner and Retry button.
- **`frontend/.../TopBar.jsx`**: Silent catch on notifications fetch and mark-all-read replaced with user-facing error state and console logging.
- **`frontend/.../AssessmentTakingPage.jsx`**: Swallowed catch in timer submission replaced with error banner and retry action.
- **`frontend/.../ClassroomDetailPage.jsx`**: Silent catches on material download and enrollment removal replaced with toast error notifications.
- **`frontend/.../AdminPage.jsx`**: Swallowed catch on stats fetch replaced with visible error state and retry button.
- **`frontend/.../AssessmentResultPage.jsx`**: Swallowed catch on question review fetch replaced with visible error card and reload button.
- **`frontend/.../LiveSessionPage.jsx`**: Silent catches in WebRTC event hooks replaced with proper logger warnings.

### 2. Frontend Object-Array Literals
All array literals of objects in `frontend/src` were audited across pages, components, hooks, and utils:
- `Sidebar.jsx`: UI navigation route items (`title`, `path`, `icon`).
- `AssessmentAttemptsListPage.jsx`: Table column definitions (`header`, `accessor`).
- `AssessmentBuilderPage.jsx`: Empty form question templates for user input.
- **Zero fake, canned, or mock data arrays exist in production frontend code.**

### 3. Pattern Scan
- `backend/src/main`: **0 hits** for `mock|fake|dummy|sample|placeholder|hardcod|lorem|fallback|simulate|TODO|FIXME`.
- `frontend/src` (excluding tests): Only standard HTML `<input placeholder="...">` attributes and user-facing 503 error text explaining that mock fallbacks are prohibited by policy.
