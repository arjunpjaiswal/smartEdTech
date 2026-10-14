# SmartEdTech API Authorization Matrix

This document provides the definitive, verified access-control matrix for all backend endpoints across SmartEdTech. Every endpoint enforces role-based access control (RBAC) and data-ownership isolation at the backend service layer.

---

## 1. Authentication & Session Management (`/api/v1/auth`)

| Method | Endpoint | Allowed Roles | Ownership / Context Guard | Unauthorized Response | Verified Test Suite |
|---|---|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public (Unauthenticated) | STUDENT or TEACHER registration only. ADMIN disallowed in payload. | 400 Bad Request / 409 Conflict | `AuthIntegrationTest` |
| `POST` | `/api/v1/auth/login` | Public (Unauthenticated) | Active account check; deactivated accounts rejected. | 401 Unauthorized / 403 Forbidden | `AuthIntegrationTest` |
| `POST` | `/api/v1/auth/refresh` | Public with Cookie | Requires `X-Requested-With` header, allowed `Origin`, valid unrevoked HttpOnly token. Rotates token; revokes entire family on reuse. | 401 Unauthorized / 403 Forbidden | `AuthIntegrationTest` |
| `POST` | `/api/v1/auth/logout` | Authenticated or Cookie | Invalidates caller's active refresh token in database. | 204 No Content | `AuthIntegrationTest` |
| `GET` | `/api/v1/auth/me` | Authenticated | Returns caller's profile based strictly on JWT principal. | 401 Unauthorized | `AuthIntegrationTest` |

---

## 2. System Configuration & Integrations (`/api/v1/system`)

| Method | Endpoint | Allowed Roles | Ownership / Context Guard | Unauthorized Response | Verified Test Suite |
|---|---|---|---|---|---|
| `GET` | `/api/v1/system/integrations` | TEACHER, ADMIN | Student access prohibited. Reports status without leaking keys. | 403 Forbidden | `AuthorizationMatrixIntegrationTest` |

---

## 3. Platform Administration (`/api/v1/admin`)

| Method | Endpoint | Allowed Roles | Ownership / Context Guard | Unauthorized Response | Verified Test Suite |
|---|---|---|---|---|---|
| `GET` | `/api/v1/admin/users` | ADMIN | Only system administrators. Students and teachers rejected. | 403 Forbidden | `AuthorizationMatrixIntegrationTest`, `AdminAndDeactivationIntegrationTest` |
| `PATCH` | `/api/v1/admin/users/{id}/status` | ADMIN | Admin cannot deactivate own account. Modifies active flag in DB. | 400 Bad Request / 403 Forbidden | `AdminAndDeactivationIntegrationTest` |
| `GET` | `/api/v1/admin/stats` | ADMIN | Global aggregates across users, classrooms, sessions, and submissions. | 403 Forbidden | `AuthorizationMatrixIntegrationTest`, `AdminAndDeactivationIntegrationTest` |

---

## 4. Classrooms & Enrollments (`/api/v1/classrooms`)

| Method | Endpoint | Allowed Roles | Ownership / Context Guard | Unauthorized Response | Verified Test Suite |
|---|---|---|---|---|---|
| `POST` | `/api/v1/classrooms` | TEACHER, ADMIN | Creator assigned as classroom teacher. Students rejected. | 403 Forbidden | `ClassroomIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `GET` | `/api/v1/classrooms` | Authenticated | Scoped: Teachers see created classes; students see enrolled classes. | 401 Unauthorized | `ClassroomIntegrationTest` |
| `GET` | `/api/v1/classrooms/{id}` | Authenticated | Enrolled students, classroom teacher, or ADMIN only. Unenrolled students rejected. | 403 Forbidden | `ClassroomIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `PUT` | `/api/v1/classrooms/{id}` | TEACHER, ADMIN | Must be classroom owner (Teacher A cannot edit Teacher B's class). | 403 Forbidden | `ClassroomIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `PATCH` | `/api/v1/classrooms/{id}/archive` | TEACHER, ADMIN | Must be classroom owner (Teacher A cannot archive Teacher B's class). | 403 Forbidden | `ClassroomIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `POST` | `/api/v1/classrooms/join` | STUDENT | Must supply valid 8-character code. Duplicate enrollment returns 409. | 403 Forbidden / 409 Conflict | `ClassroomIntegrationTest` |
| `POST` | `/api/v1/classrooms/{id}/students` | TEACHER, ADMIN | Must be classroom owner. Can enroll by email or student ID. Duplicate returns 409. | 403 Forbidden / 409 Conflict | `ClassroomIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `DELETE` | `/api/v1/classrooms/{id}/students/{studentId}` | TEACHER, ADMIN | Must be classroom owner. Removes student from enrollment table. | 403 Forbidden / 404 Not Found | `ClassroomIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `GET` | `/api/v1/classrooms/{id}/students` | Authenticated | Enrolled student, classroom teacher, or ADMIN. | 403 Forbidden | `ClassroomIntegrationTest` |

---

## 5. Live Sessions & Attendance (`/api/v1/sessions`)

| Method | Endpoint | Allowed Roles | Ownership / Context Guard | Unauthorized Response | Verified Test Suite |
|---|---|---|---|---|---|
| `POST` | `/api/v1/classrooms/{id}/sessions` | TEACHER, ADMIN | Must be classroom owner. Students and non-owner teachers rejected. | 403 Forbidden | `SessionIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `GET` | `/api/v1/classrooms/{id}/sessions` | Authenticated | Enrolled student, classroom teacher, or ADMIN. | 403 Forbidden | `SessionIntegrationTest` |
| `GET` | `/api/v1/sessions/upcoming` | Authenticated | Scoped to user's enrolled or instructed classrooms. | 401 Unauthorized | `SessionIntegrationTest` |
| `GET` | `/api/v1/sessions/{id}` | Authenticated | Enrolled student, classroom teacher, or ADMIN. | 403 Forbidden | `SessionIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `PUT` | `/api/v1/sessions/{id}` | TEACHER, ADMIN | Must be classroom teacher of session or ADMIN. | 403 Forbidden | `AuthorizationMatrixIntegrationTest` |
| `PATCH` | `/api/v1/sessions/{id}/cancel` | TEACHER, ADMIN | Must be classroom teacher of session or ADMIN. | 403 Forbidden | `SessionIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `PATCH` | `/api/v1/sessions/{id}/end` | TEACHER, ADMIN | Must be classroom teacher of session or ADMIN. | 403 Forbidden | `SessionIntegrationTest` |
| `POST` | `/api/v1/sessions/{id}/join` | Authenticated | Must be enrolled student or classroom teacher. Issues short-lived JaaS RS256 token (503 if credentials missing). | 403 Forbidden / 503 Not Configured | `SessionIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `POST` | `/api/v1/sessions/{id}/leave` | Authenticated | Caller's attendance record updated. | 403 Forbidden | `SessionIntegrationTest` |
| `POST` | `/api/v1/sessions/{id}/attendance/heartbeat` | Authenticated | Caller's attendance duration and last seen updated. | 403 Forbidden | `SessionIntegrationTest` |
| `GET` | `/api/v1/sessions/{id}/attendance` | TEACHER, ADMIN | Must be classroom teacher or ADMIN. Students rejected. | 403 Forbidden | `SessionIntegrationTest`, `AuthorizationMatrixIntegrationTest` |

---

## 6. Study Materials & File Storage (`/api/v1/materials`)

| Method | Endpoint | Allowed Roles | Ownership / Context Guard | Unauthorized Response | Verified Test Suite |
|---|---|---|---|---|---|
| `POST` | `/api/v1/classrooms/{id}/materials` | TEACHER, ADMIN | Must be classroom teacher or ADMIN. Magic byte & extension validation enforced. | 400 Bad Request / 403 Forbidden | `MaterialIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `GET` | `/api/v1/classrooms/{id}/materials` | Authenticated | Enrolled students, classroom teacher, or ADMIN. | 403 Forbidden | `MaterialIntegrationTest` |
| `GET` | `/api/v1/materials/{id}` | Authenticated | Enrolled student, classroom teacher, or ADMIN. | 403 Forbidden | `MaterialIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `GET` | `/api/v1/materials/{id}/download` | Authenticated | Enrolled student, classroom teacher, or ADMIN. Streamed safely from internal storage. | 403 Forbidden | `MaterialIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `PUT` | `/api/v1/materials/{id}` | TEACHER, ADMIN | Must be classroom teacher or ADMIN. Modifies title/description. | 403 Forbidden | `AuthorizationMatrixIntegrationTest` |
| `DELETE` | `/api/v1/materials/{id}` | TEACHER, ADMIN | Must be classroom teacher or ADMIN. Deletes file from storage and database row. | 403 Forbidden | `MaterialIntegrationTest`, `AuthorizationMatrixIntegrationTest` |

---

## 7. Assessments & AI Quiz Generation (`/api/v1/assessments`)

| Method | Endpoint | Allowed Roles | Ownership / Context Guard | Unauthorized Response | Verified Test Suite |
|---|---|---|---|---|---|
| `POST` | `/api/v1/classrooms/{id}/assessments` | TEACHER, ADMIN | Must be classroom teacher or ADMIN. Students rejected. | 403 Forbidden | `AssessmentIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `GET` | `/api/v1/classrooms/{id}/assessments` | Authenticated | Enrolled students see PUBLISHED assessments only; teachers see all. | 403 Forbidden | `AssessmentIntegrationTest` |
| `GET` | `/api/v1/assessments/{id}` | Authenticated | Enrolled students receive questions without answer keys, rubrics, or model answers. | 403 Forbidden | `AssessmentIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `PUT` | `/api/v1/assessments/{id}` | TEACHER, ADMIN | Must be classroom teacher or ADMIN. | 403 Forbidden | `AssessmentIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `PUT` | `/api/v1/assessments/{id}/publish` | TEACHER, ADMIN | Must be classroom teacher or ADMIN. Minimum 1 question required. | 400 Bad Request / 403 Forbidden | `AssessmentIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `PUT` | `/api/v1/assessments/{id}/archive` | TEACHER, ADMIN | Must be classroom teacher or ADMIN. | 403 Forbidden | `AssessmentIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `POST` | `/api/v1/assessments/{id}/questions` | TEACHER, ADMIN | Must be classroom teacher or ADMIN. Only allowed on DRAFT assessments. | 400 Bad Request / 403 Forbidden | `AssessmentIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `PUT` | `/api/v1/assessments/{id}/questions/{qId}` | TEACHER, ADMIN | Must be classroom teacher or ADMIN. Only allowed on DRAFT assessments. | 400 Bad Request / 403 Forbidden | `AssessmentIntegrationTest` |
| `DELETE` | `/api/v1/assessments/{id}/questions/{qId}` | TEACHER, ADMIN | Must be classroom teacher or ADMIN. Only allowed on DRAFT assessments. | 400 Bad Request / 403 Forbidden | `AssessmentIntegrationTest` |
| `POST` | `/api/v1/ai/generate-questions` | TEACHER, ADMIN | Teacher must own referenced study material. Students rejected. | 403 Forbidden | `AssessmentIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `GET` | `/api/v1/ai/jobs/{id}` | Authenticated | Only job owner, classroom teacher, or ADMIN. Other teachers rejected. | 403 Forbidden | `AssessmentIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `POST` | `/api/v1/ai/jobs/{id}/retry` | Authenticated | Only job creator or ADMIN on FAILED jobs. | 403 Forbidden | `AssessmentIntegrationTest` |

---

## 8. Assessment Attempts & Grading (`/api/v1/attempts`, `/api/v1/evaluations`)

| Method | Endpoint | Allowed Roles | Ownership / Context Guard | Unauthorized Response | Verified Test Suite |
|---|---|---|---|---|---|
| `POST` | `/api/v1/assessments/{id}/start` | STUDENT, ADMIN | Enrolled student only. Published status required. Max attempt limits enforced. Teachers rejected. | 400 Bad Request / 403 Forbidden / 409 Conflict | `AssessmentIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `POST` | `/api/v1/attempts/{id}/answers` | STUDENT, ADMIN | Must be attempt owner. Attempt must be IN_PROGRESS. Deadline enforced. | 400 Bad Request / 403 Forbidden | `AssessmentIntegrationTest` |
| `POST` | `/api/v1/attempts/{id}/submit` | STUDENT, ADMIN | Must be attempt owner. Objective MCQs auto-graded immediately. | 400 Bad Request / 403 Forbidden | `AssessmentIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `GET` | `/api/v1/attempts/{id}` | Authenticated | Student owner, classroom teacher, or ADMIN only. Student B cannot view Student A's attempt. | 403 Forbidden | `AssessmentIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `GET` | `/api/v1/assessments/{id}/attempts` | Authenticated | Students see own attempts; classroom teacher or ADMIN sees all. Teacher B cannot view Teacher A's assessment attempts. | 403 Forbidden | `AssessmentIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `PUT` | `/api/v1/evaluations/{id}` | TEACHER, ADMIN | Classroom teacher or ADMIN only. Students and other teachers rejected. | 403 Forbidden | `AssessmentIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `GET` | `/api/v1/attempts/{id}/remediation` | Authenticated | Student attempt owner, classroom teacher, or ADMIN only. | 403 Forbidden | `AssessmentIntegrationTest` |
| `GET` | `/api/v1/students/{studentId}/weak-concepts` | Authenticated | Student self, their classroom teacher, or ADMIN only. Other students rejected. | 403 Forbidden | `AuthorizationMatrixIntegrationTest` |

---

## 9. Real-Time Polls & STOMP Channels (`/api/v1/sessions/{id}/polls`, `/ws`)

| Channel / Endpoint | Allowed Roles | Ownership / Context Guard | Unauthorized Response | Verified Test Suite |
|---|---|---|---|---|
| `POST /api/v1/sessions/{id}/polls` | TEACHER, ADMIN | Must be classroom teacher of session. Students rejected. | 403 Forbidden | `PollIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `PATCH .../polls/{id}/open` | TEACHER, ADMIN | Must be classroom teacher of session. | 403 Forbidden | `PollIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `PATCH .../polls/{id}/close` | TEACHER, ADMIN | Must be classroom teacher of session. | 403 Forbidden | `PollIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `PATCH .../polls/{id}/visibility` | TEACHER, ADMIN | Must be classroom teacher of session. | 403 Forbidden | `PollIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `POST .../polls/{id}/vote` | Authenticated | Enrolled student only. Poll must be OPEN. Duplicate votes rejected with 409 and DB unique constraint. | 400 Bad Request / 403 Forbidden / 409 Conflict | `PollIntegrationTest`, `AuthorizationMatrixIntegrationTest` |
| `GET .../polls` | Authenticated | Enrolled students see vote counts only if closed or visibility enabled; teachers always see counts. | 403 Forbidden | `PollIntegrationTest` |
| `STOMP CONNECT /ws` | Authenticated | Valid, unexpired JWT required in header. Deactivated accounts rejected. | AccessDeniedException | `WebSocketAndDiscussionIntegrationTest` |
| `STOMP SUB /topic/sessions/{id}/polls` | Authenticated | Enrolled students, classroom teacher, or ADMIN. | AccessDeniedException | `WebSocketAndDiscussionIntegrationTest` |
| `STOMP SUB .../polls/teacher` | TEACHER, ADMIN | Classroom teacher or ADMIN only. Students rejected. | AccessDeniedException | `WebSocketAndDiscussionIntegrationTest` |
| `STOMP SEND /app/sessions/{id}/chat` | Authenticated | Enrolled students, classroom teacher, or ADMIN. Sender strictly taken from principal. Rate limited. | AccessDeniedException | `WebSocketAndDiscussionIntegrationTest` |

---

## 10. Study Buddy Chatbot (`/api/v1/chat/conversations`)

| Method | Endpoint | Allowed Roles | Ownership / Context Guard | Unauthorized Response | Verified Test Suite |
|---|---|---|---|---|---|
| `GET` | `/api/v1/chat/conversations` | STUDENT, ADMIN | Scoped to caller's student ID. Teachers rejected. | 403 Forbidden | `AuthorizationMatrixIntegrationTest` |
| `POST` | `/api/v1/chat/conversations` | STUDENT, ADMIN | Student must be enrolled in classroom of referenced study material. Teachers rejected. | 403 Forbidden | `AuthorizationMatrixIntegrationTest` |
| `GET` | `/api/v1/chat/conversations/{id}` | STUDENT, ADMIN | Student owner or ADMIN only. Student B receives 404 (scoped query). | 403 Forbidden / 404 Not Found | `AuthorizationMatrixIntegrationTest` |
| `DELETE` | `/api/v1/chat/conversations/{id}` | STUDENT, ADMIN | Student owner or ADMIN only. | 403 Forbidden / 404 Not Found | `AuthorizationMatrixIntegrationTest` |
| `GET` | `/api/v1/chat/conversations/{id}/messages` | STUDENT, ADMIN | Student owner or ADMIN only. | 403 Forbidden / 404 Not Found | `AuthorizationMatrixIntegrationTest` |
| `POST` | `/api/v1/chat/conversations/{id}/messages` | STUDENT, ADMIN | Student owner or ADMIN only. 4000 char limit. Dispatches to AiService failover. | 400 Bad Request / 403 Forbidden / 404 Not Found | `AuthorizationMatrixIntegrationTest` |

---

## 11. Notifications (`/api/v1/notifications`)

| Method | Endpoint | Allowed Roles | Ownership / Context Guard | Unauthorized Response | Verified Test Suite |
|---|---|---|---|---|---|
| `GET` | `/api/v1/notifications` | Authenticated | Strictly scoped to caller's principal ID. | 401 Unauthorized | `MaterialIntegrationTest`, `SessionIntegrationTest` |
| `GET` | `/api/v1/notifications/unread-count` | Authenticated | Strictly counts unread notifications for caller's principal ID. | 401 Unauthorized | `MaterialIntegrationTest` |
| `PATCH` | `/api/v1/notifications/{id}/read` | Authenticated | Must belong to caller. | 401 Unauthorized / 404 Not Found | `MaterialIntegrationTest` |
| `PATCH` | `/api/v1/notifications/read-all` | Authenticated | Marks all caller's notifications as read. | 401 Unauthorized | `MaterialIntegrationTest` |
