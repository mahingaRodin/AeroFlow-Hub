# AeroFlow-Hub: Enterprise Flight Crew & Resource Approval Workflow System

> Production-grade enterprise backend system designed to digitize manual airline approval workflows, manage staff travel clearance, and parse SITA/SSIM airline message formats.

---

## 🏗️ Architecture & Core Modules

* **Workflow Engine:** State-machine based electronic approval pipeline for crew travel clearances and petty cash requests.
* **Integration Parser:** Robust text-stream parser for SITA Type B messages and SSIM flight schedules with automated error quarantine.
* **Security Layer:** Role-Based Access Control (RBAC) integrated with enterprise identity standards.

---

## 🚀 Tech Stack
* **Language & Runtime:** Java 21, Spring Boot 3.x
* **Persistence:** PostgreSQL, Spring Data JPA, Hibernate
* **Security:** Spring Security, JWT Token Validation
* **Testing:** JUnit 5, Mockito, Spring Boot Test
* **DevOps:** Docker, Maven, GitHub Actions CI

---

## Repository structure

* `be/` — Java 21 / Spring Boot REST API, JPA models, JWT security, workflow, and parser scaffolds.
* `fe/` — Vue 3 / TypeScript crew operations portal built with Vite.

## Getting started

### Requirements
* JDK 21 and Maven 3.9+.
* Node.js 20.19+ or 22.12+ and npm.
* PostgreSQL with an `aeroflow_db` database.

### Configure the backend
Create a local environment file from the template:

```powershell
cd be
Copy-Item .env.example .env
```

Edit `be/.env` and set your PostgreSQL password. Generate a Base64-encoded JWT
signing key with at least 256 bits of entropy:

```bash
openssl rand -base64 32
```

Paste the complete output (including any trailing `=` padding) into
`JWT_SECRET_BASE64` in `be/.env`. The application accepts standard Base64 or
Base64URL encoding and requires at least 32 decoded bytes. Ensure IntelliJ's
Run Configuration does not override this value with a stale or placeholder
environment variable. Spring Boot
imports that file whether you start the service from the repository root, the
`be` directory, or an IntelliJ run configuration whose working directory is
either location. The `.env` file is ignored by Git; do not commit real
credentials. Production deployments should inject secrets through the
deployment's secret manager instead.

SMTP is optional for startup. To enable OTP email delivery, set `MAIL_HOST`,
`MAIL_PORT`, `MAIL_USERNAME`, and `MAIL_PASSWORD` in `be/.env` (or provide
them through the deployment secret manager). The `spring-boot-starter-mail`
dependency supplies the Jakarta Mail classes required by `MailConfig`.

Start the backend from `be/`:

```powershell
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. `DATABASE_URL`,
`DATABASE_USERNAME`, `DATABASE_POOL_SIZE`, and `JWT_EXPIRATION_MS` can be set
to override their defaults. `CORS_ALLOWED_ORIGINS` accepts a comma-separated
list of frontend origins; the default allows the local Vite server.

### Configure the frontend
In another terminal:

```bash
cd fe
npm install
npm run dev
```

Open the local URL printed by Vite (normally `http://localhost:5173`). Requests
to `/api` are proxied to `http://localhost:8080`; override the target with
`VITE_API_PROXY_TARGET`. For a deployment with a separately hosted API, set
`VITE_API_BASE_URL` to the API base (for example
`https://api.example.com/api/v1`) and configure `CORS_ALLOWED_ORIGINS` on the
backend to include the frontend origin. See `fe/.env.example` for the frontend
environment variables.

### Initial API and workflow
* `POST /api/v1/auth/login` — authenticate a provisioned user and receive a JWT.
* `POST /api/v1/travel-clearance-requests` — create a draft clearance request.
* `GET /api/v1/travel-clearance-requests` — list the authenticated user's requests.
* `POST /api/v1/travel-clearance-requests/{id}/submit` — submit a draft for review.
* `POST /api/v1/travel-clearance-requests/{id}/approve` or `/reject` — supervisor
  decisions (requires the `ROLE_SUPERVISOR` authority).

The frontend currently supports sign-in, creating and viewing personal travel
clearances, and submitting drafts for approval. User/role provisioning is a
deployment responsibility. Hibernate schema update is enabled for the initial
bootstrap; switch to versioned database migrations before production. SITA Type
B and SSIM parsing currently provides stream framing/record-type recognition
and preserves raw content with line-numbered issues. Format-specific field
extraction remains to be implemented.

### Message ingestion and quarantine
Authenticated users may submit JSON payloads to `POST /api/v1/ingestion/sita`
or `POST /api/v1/ingestion/ssim` (maximum payload: 1,000,000 characters).
Successful parses return `200`; inputs with parse issues return `202` and are
stored in the quarantine. Both endpoints accept a JSON body such as
`{"payload":"ZCZC AB123\\nmessage body\\nNNNN"}`. Supervisors and admins can
page through quarantine summaries at `GET /api/v1/ingestion/quarantine`; only
admins can view a detail record, including its raw payload, at
`GET /api/v1/ingestion/quarantine/{id}`. Raw payloads and issue excerpts are
sensitive operational data. Quarantine entries expire after 30 days by
default; `QUARANTINE_RETENTION_DAYS` configures the retention period and
`QUARANTINE_CLEANUP_CRON` configures the daily deletion schedule.