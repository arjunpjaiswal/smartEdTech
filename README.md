# SmartEdTech

SmartEdTech is a production-grade, AI-powered interactive classroom and learning management platform featuring:
- **Live Video Classes**: Jitsi Meet (8x8 JaaS) integration with RS256 backend JWT signing and automated attendance tracking.
- **Real-Time Classroom Interaction**: STOMP over WebSocket for live session polls and structured chat discussions.
- **AI Assessment Generation**: Asynchronous question generation from uploaded PDFs with automated failover between Google Gemini and Groq (via Apache PDFBox text extraction).
- **Automated & Subjective Grading**: Instant auto-grading for objective MCQs, rubric-driven AI grading for subjective responses, and teacher mark adjustments.
- **Weakness Analytics & Remediation**: Topic-level weakness tracking and tailored study plans.
- **AI Study Buddy**: In-context pedagogical conversational assistant grounded in classroom study materials.

Built for three primary roles:
- `ADMIN`: Platform monitoring, user directory management, and system integration health.
- `TEACHER`: Classroom management, scheduling live sessions, uploading study materials, authoring assessments, conducting polls, and grading.
- `STUDENT`: Classroom enrollment via join codes, attending live sessions, taking timed assessments, viewing personalized remediation, and chatting with the AI Study Buddy.

---

## 1. Prerequisites

Ensure the following tools are installed on your host system:
- **Java**: OpenJDK 21 or Oracle JDK 21 (`java -version` should report 21).
- **Node.js**: Node.js 18+ (tested on v20 and v22) & npm.
- **Database**: MySQL 8.0+ running on port 3306.
- **Build Tools**: The repository includes the Gradle 8.12 wrapper (`gradlew` / `gradlew.bat`). No separate Gradle installation is required.

---

## 2. MySQL Database Setup

The platform requires MySQL 8.0+. To follow least-privilege security practices, use the provided setup script `docs/mysql-setup.sql` to create the databases and a dedicated application user:

1. Open `docs/mysql-setup.sql` and replace the placeholder password `'CHANGE_ME_BEFORE_RUNNING'` with your desired secure database password.
2. Execute the script as your MySQL administrator:
   ```bash
   mysql -u root -p < docs/mysql-setup.sql
   ```
   Or execute directly in your MySQL client:
   ```sql
   CREATE DATABASE IF NOT EXISTS smartedtech CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE DATABASE IF NOT EXISTS smartedtech_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER IF NOT EXISTS 'smartedtech_user'@'localhost' IDENTIFIED BY 'CHANGE_ME_BEFORE_RUNNING';
   GRANT ALL PRIVILEGES ON smartedtech.* TO 'smartedtech_user'@'localhost';
   GRANT ALL PRIVILEGES ON smartedtech_test.* TO 'smartedtech_user'@'localhost';
   FLUSH PRIVILEGES;
   ```

### Database Migrations (Flyway)
The backend uses Flyway for strict, reproducible database migrations (`spring.jpa.hibernate.ddl-auto=validate`). Tables are never auto-created or mutated by Hibernate.

Migrations applied sequentially on startup:
- `V1__init_auth.sql`: Core users (`users`), roles, and hashed refresh token tracking (`refresh_tokens`).
- `V2__core_classroom.sql`: Classrooms, student enrollments, live sessions, session attendance, study materials, and notifications.
- `V3__ai_assessments.sql`: Assessments, questions, answer options, attempts, student answers, evaluations, weak concepts, remediation plans, and async generation jobs.
- `V4__polls_and_discussion.sql`: Live session polls, poll options, unique poll votes, and discussion messages.
- `V5__study_buddy_and_analytics.sql`: AI study buddy chat conversations and persisted message exchanges.

---

## 3. Environment Variables & Configuration

Create a `.env` file in the project root or configure your deployment environment using the variables below. You can copy `.env.example` as a starting template:

```bash
cp .env.example .env
```

### Configuration Reference

| Variable | Required | Default | Description |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | **Yes** | `jdbc:mysql://localhost:3306/smartedtech?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` | JDBC URL for MySQL application database. |
| `SPRING_DATASOURCE_USERNAME` | **Yes** | `smartedtech_user` | Dedicated MySQL username created by `docs/mysql-setup.sql`. |
| `SPRING_DATASOURCE_PASSWORD` | **Yes** | `CHANGE_ME_BEFORE_RUNNING` | Dedicated MySQL password configured during setup. |
| `JWT_SECRET` | **Yes** | - | Cryptographic secret key for signing HS256 access tokens. Minimum 32 characters (256 bits). |
| `APP_CORS_ALLOWED_ORIGINS` | No | `http://localhost:5173,http://localhost:3000` | Comma-separated list of permitted web origins for CORS and CSRF verification. |
| `APP_FILE_STORAGE_PATH` | No | `./uploads` | Local filesystem directory for storing uploaded materials. Path traversal protected. |
| `APP_BOOTSTRAP_ADMIN_EMAIL` | No | `admin@smartedtech.com` | Email address for initial bootstrap administrator account. |
| `APP_BOOTSTRAP_ADMIN_PASSWORD` | No | `AdminSecurePassword123!` | Password for initial bootstrap administrator account (minimum 8 characters). |
| `GEMINI_API_KEY` | Optional | - | API key for Google Gemini (enables native PDF quiz generation, grading, and chat). |
| `GEMINI_MODEL` | No | `gemini-1.5-flash` | Gemini model identifier. |
| `GROQ_API_KEY` | Optional | - | API key for Groq (enables text quiz generation, grading, chat, and Gemini failover). |
| `GROQ_MODEL` | No | `llama-3.3-70b-versatile` | Groq model identifier. |
| `JITSI_APP_ID` | Optional | - | 8x8 JaaS App ID (format: `vpaas-magic-cookie-...`). |
| `JITSI_KID` | Optional | - | 8x8 JaaS Key ID (KID) for the registered API key. |
| `JITSI_PRIVATE_KEY_PATH` | Optional | - | Path to the downloaded JaaS RSA private key file (`.pk` or `.pem`). |

> **Note on Optional Integrations**: The backend starts cleanly without AI or Jitsi keys. If an optional integration is unconfigured, endpoints that rely on it return HTTP `503 INTEGRATION_NOT_CONFIGURED` with a message naming the missing environment variable. Production code never falls back to fake or mock data. Check status anytime at `GET /api/v1/system/integrations`.

---

## 4. Running the Application

### 4.1 Running the Backend

From the `backend/` directory:

```bash
# On Linux / macOS:
./gradlew bootRun

# On Windows (PowerShell / CMD):
.\gradlew.bat bootRun
```

The Spring Boot backend will start on `http://localhost:8080`.
- **API Base**: `http://localhost:8080/api/v1`
- **OpenAPI Swagger UI**: `http://localhost:8080/swagger-ui`
- **Actuator Health Check**: `http://localhost:8080/actuator/health`

### 4.2 Running the Frontend

From the `frontend/` directory:

```bash
# Install dependencies:
npm install

# Start Vite development server:
npm run dev
```

The frontend SPA will run on `http://localhost:5173`.
Vite is pre-configured to proxy `/api` and `/ws` requests to `http://localhost:8080`, providing a seamless single-origin developer experience without CORS friction.

---

## 5. Running the Test Suites

### 5.1 Backend Tests (`./gradlew test`)

The backend test suite executes against the real MySQL test database (`smartedtech_test`). Tests do not use H2 or in-memory database mocks.

```bash
cd backend

# Linux / macOS
./gradlew test

# Windows
.\gradlew.bat test
```

To run a specific test class:
```bash
./gradlew test --tests "*AuthorizationMatrixIntegrationTest*"
```

**Coverage includes**:
- Registration, login, BCrypt password verification, and JWT access token issuance.
- Refresh token rotation, token family tracking, and replay detection.
- Cross-tenant role authorization matrix (verifying 403 Forbidden on wrong role and wrong owner across all resources).
- Classroom creation, enrollment limits, unique join codes, and duplicate enrollment rejection (409 Conflict).
- Material uploads, extension and magic byte validation, size ceilings, and safe UUID storage.
- Session scheduling, attendance tracking, and 503 handling when Jitsi credentials are absent.
- Real-time STOMP WebSocket authentication, subscription authorization, and concurrent client messaging.
- Objective MCQ auto-grading and answer key privacy (ensuring students never receive answer keys or model answers).

### 5.2 Frontend Tests (`npm run test`)

Executed using Vitest and React Testing Library:

```bash
cd frontend
npm run test
```

### 5.3 Frontend Production Build (`npm run build`)

Compiles and bundles the production React application with Tailwind CSS:

```bash
cd frontend
npm run build
```

Production assets are emitted to `frontend/dist/`.

---

## 6. Live Video (8x8 JaaS / Jitsi Meet) Integration

SmartEdTech uses **8x8 Jitsi as a Service (JaaS)** for secure, in-browser video classrooms. The backend signs short-lived RS256 JSON Web Tokens; private keys are never transmitted to the browser.

### Setting Up JaaS Credentials
1. Register for an account at [jaas.8x8.vc](https://jaas.8x8.vc).
2. In the JaaS Console, navigate to **API Keys** and generate an API key pair.
3. Note your **AppID** (e.g. `vpaas-magic-cookie-xxxx`) and **Key ID (KID)**.
4. Download the generated private key file (e.g., `jaas_private_key.pk`).
5. Save the private key in a secure path accessible to the backend.
6. Configure environment variables in `.env`:
   ```env
   JITSI_APP_ID=vpaas-magic-cookie-xxxx
   JITSI_KID=vpaas-magic-cookie-xxxx/yyyy
   JITSI_PRIVATE_KEY_PATH=/absolute/path/to/jaas_private_key.pk
   ```

### Self-Hosted Jitsi Alternative
To connect SmartEdTech to a self-hosted Jitsi instance:
1. Enable `mod_auth_token` on your Prosody instance configured with RSA key verification.
2. Provide your server's domain in `JITSI_APP_ID`, public key ID in `JITSI_KID`, and the private key in `JITSI_PRIVATE_KEY_PATH`.
3. In `frontend/src/pages/LiveSessionPage.jsx`, update the Jitsi External API domain to point to your self-hosted host.

---

## 7. AI Integrations (Google Gemini & Groq)

SmartEdTech implements a dual-provider AI pipeline with automated failover:

### 7.1 Provider Capabilities
- **Google Gemini** (`GEMINI_API_KEY`): Used natively for direct PDF parsing and multi-turn educational dialogues.
- **Groq** (`GROQ_API_KEY`): Ultra-low-latency inference using `llama-3.3-70b-versatile`. Because Groq accepts text only, the backend automatically extracts PDF text using Apache PDFBox before sending requests.

### 7.2 Failover Policy
If a request to Gemini encounters transient network errors, rate limits (HTTP 429), or upstream 5xx server issues, `AiService` automatically falls back to Groq without manual user intervention. Permanent errors (e.g. 400 Bad Request, 401 Unauthorized, or invalid prompt syntax) are never failed over and propagate immediately.

### 7.3 Asynchronous Job Architecture
AI question generation and subjective grading execute asynchronously:
1. `POST /api/v1/ai/generate-questions` validates caller authorization and returns HTTP `202 Accepted` with a `jobId`.
2. The task executes on a bounded background thread executor (`generationTaskExecutor`).
3. The client polls `GET /api/v1/ai/jobs/{jobId}` until the status is `SUCCEEDED` or `FAILED`.
4. If a job fails, the real error code and upstream message are persisted in MySQL and can be re-triggered via `POST /api/v1/ai/jobs/{id}/retry`.

---

## 8. Production Deployment Notes

### 8.1 Monolithic Architecture
SmartEdTech is deployed as a single stateless Spring Boot executable JAR backed by MySQL:
```bash
cd backend
./gradlew bootJar
java -jar build/libs/smartedtech-backend-0.0.1-SNAPSHOT.jar
```

### 8.2 Reverse Proxy Configuration (Nginx / Caddy)
In production, place an SSL-terminating reverse proxy in front of the services:
- Route `/api` and `/ws` to the Spring Boot backend (`http://localhost:8080`).
- Route static frontend requests (`/`) to the static build files in `frontend/dist/`.
- Ensure WebSocket upgrade headers are passed:
  ```nginx
  location /ws/ {
      proxy_pass http://localhost:8080/ws/;
      proxy_http_version 1.1;
      proxy_set_header Upgrade $http_upgrade;
      proxy_set_header Connection "Upgrade";
      proxy_set_header Host $host;
  }
  ```

### 8.3 Cookie & HTTPS Security
In production environments:
- The refresh token cookie automatically has `Secure=true` enabled when running over HTTPS.
- Set `APP_CORS_ALLOWED_ORIGINS` to your production domain (e.g. `https://smartedtech.example.com`).
- The in-memory STOMP broker is sufficient for single-instance deployments. For horizontal scaling across multiple instances, configure a shared external STOMP message broker (such as RabbitMQ with the STOMP plugin enabled).
