# SmartEdTech Phase Prompts

## Phase 1 — Runnable Foundation with Real Authentication
- Inspect workspace and installed tools.
- Create repo structure.
- Backend: Spring Boot 3.x (Java 21, Gradle with wrapper, Groovy DSL).
- Flyway V1 migration for users and refresh_tokens. spring.jpa.hibernate.ddl-auto=validate.
- Security: BCrypt, 15-min JWT access token, rotating hashed refresh token in HttpOnly cookie, reuse detection, CSRF protection on cookie endpoints, rate limiting on auth endpoints, admin bootstrap from env vars.
- Endpoints: POST /auth/register, POST /auth/login, POST /auth/refresh, POST /auth/logout, GET /auth/me, GET /api/v1/system/integrations. Global exception handler.
- Frontend: Vite + React + Tailwind, router, Axios client (in-memory token, refresh on 401 & load), auth context, protected routes, app shell, public pages.
- Reusable UI components.
- Verification: gradlew test, gradlew bootRun + Flyway V1, curl auth flow, npm run build + E2E browser test.
- Self-audit and update PROJECT_STATUS.md.

## Phase 2 — Core Classroom Features, Persistence & Backend Authorization
- Flyway V2 migration: classrooms, classroom_enrollments, live_sessions, attendance, study_materials, notifications. Proper FKs, indexes, timestamps.
- Classroom APIs: create, edit, archive, list (scoped by role), details, join by code (students), teacher enroll/remove, student list. Duplicate enrollment returns 409 and blocked by unique constraint.
- Session APIs: create/edit/cancel/end sessions, list upcoming, details. /join endpoint verifies access and issues Jitsi JWT (RS256 JaaS). If Jitsi env vars are missing, return 503 INTEGRATION_NOT_CONFIGURED. Attendance recording and heartbeats.
- Material APIs: upload PDF/doc (content magic byte & extension validation, size limit, ownership), safe server-generated filenames behind FileStorage, list, metadata edit, authorized download, delete. No path traversal, no internal paths in responses.
- Notifications: create on session scheduled, material uploaded, enrollment; list, mark read.
- Admin APIs: list/search users, activate/deactivate, platform stats. Deactivated users cannot log in, refresh, or use access tokens.
- Frontend pages for all of the above for each role. Real dashboards for teacher, student, admin using backend aggregation endpoints. New accounts show proper empty states. Session page embedding Jitsi or displaying real 503 message.
- Backend tests: classroom + enrollment (including duplicates), authorization (teacher A cannot touch teacher B's class, student cannot access unenrolled class), material access control, session join rules, deactivated user blocked.
## Phase 3 — Assessments & AI Quiz Generation
- Flyway V3 migration: assessments, questions, answer_options, assessment_attempts, student_answers, evaluations, weak_concepts, remediation_plans, generation_jobs.
- Assessment APIs: create, edit, archive, list, question CRUD, order index reordering, publish lifecycle.
- AI Generation: asynchronous generation jobs via Gemini/Groq, failover on transient errors, PDF text extraction via PDFBox.
- Attempt taking: student starts attempt, saves answers, auto-grades objective MCQs on submit, attempt limits enforced, score calculation.
- Frontend: Assessment builder, question management, AI generator modal, test taking interface, attempt review.

## Phase 4 — Real-time Polls and Discussion over STOMP WebSocket
- Flyway V4 migration: polls, poll_options, poll_votes (unique poll+user), discussion_messages.
- WebSocket config: /ws endpoint, JWT authentication on CONNECT, authorization on every SUBSCRIBE (only teacher of the class and enrolled students of that session), sender identity from the principal only. Rate limit message sending.
- Polls: teacher creates, opens, closes; students vote once (409 on duplicate, DB constraint as backup); live results broadcast to subscribers; results visibility rules enforced in the backend.
- Discussion: persist in MySQL, REST history endpoint (paginated), live delivery over STOMP, message length limits, basic abuse controls.
- Frontend: poll panel and discussion panel on the session page, using a real STOMP client connection with reconnect and clear connection-state UI. If the connection fails, show a disconnected state. Do not fake incoming messages or results.
- Attendance: update from client join/leave events sent to the backend; teacher attendance view per session.
- Tests: subscription authorization (unauthorized subscribe rejected), impersonation attempt rejected, duplicate vote, poll lifecycle.
