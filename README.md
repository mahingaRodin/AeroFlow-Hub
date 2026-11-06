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
environment variable. Startup fails if the secret is missing, malformed, or
too short; there is no insecure default. Spring also accepts the standard
`APP_SECURITY_JWT_SECRET` environment-variable name for
`app.security.jwt.secret`. Spring Boot
imports that file whether you start the service from the repository root, the
`be` directory, or an IntelliJ run configuration whose working directory is
either location. The `.env` file is ignored by Git; do not commit real
credentials. Production deployments should inject secrets through the
deployment's secret manager instead.

Authenticator-app MFA uses a separate encryption key. Generate a second,
independent key with `openssl rand -base64 32` and set
`MFA_ENCRYPTION_KEY_BASE64` in `be/.env`. The backend fails at startup if this
key is absent or not exactly 32 decoded bytes; never reuse the JWT key.
`APP_PUBLIC_BASE_URL` controls the frontend URL in verification links and
defaults to `http://localhost:5173`.

SMTP is optional for startup. To enable email notifications, set `MAIL_HOST`,
`MAIL_PORT`, `MAIL_USERNAME`, and `MAIL_PASSWORD` in `be/.env` (or provide
them through the deployment secret manager). The `spring-boot-starter-mail`
dependency supplies the Jakarta Mail classes required by `MailConfig`. The
backend exposes `EmailNotificationService.sendTextEmail(...)` for sending
plain-text notifications, including customer email verification. SMTP delivery
should be verified with a controlled test mailbox before production.

### Provision the first administrator
Customer self-registration creates only the `ROLE_CUSTOMER` role and requires
email verification. To provision an administrator, temporarily set
`BOOTSTRAP_ADMIN_ENABLED=true` and configure
`BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_EMAIL`, and
`BOOTSTRAP_ADMIN_PASSWORD` in the ignored `be/.env`, then start the backend.
When no account matches that email, the bootstrap creates one with a BCrypt-
hashed password. If an account already exists, both its username and email
must match the configured values; the existing password is preserved. The
provisioned account receives both `ROLE_ADMIN` and `ROLE_SUPERVISOR`, enabling
administrative operations and clearance review/approval. Use a unique password
of at least 12 characters (maximum 72 UTF-8 bytes). After startup, set
`BOOTSTRAP_ADMIN_ENABLED=false` and restart the service. A username/email
conflict or mismatch fails startup rather than promoting another account.

### Database migrations
Flyway applies versioned schema migrations from `be/src/main/resources/db/migration`,
and Hibernate validates the schema at startup. Existing non-empty databases
are baselined at version 1; back up existing data and confirm its schema matches
the initial migration before upgrading. PostgreSQL integration tests use
Testcontainers and are skipped automatically when Docker is unavailable.

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
* `POST /api/v1/auth/login` — authenticate by email (or a provisioned username)
  and receive a short-lived JWT. If TOTP MFA is enabled, this returns an MFA
  challenge instead; no access token is issued until MFA succeeds.
* `POST /api/v1/auth/register` — create a customer account and email a verification
  link; the account cannot sign in until verified. The endpoint returns the same
  generic response for new and existing addresses.
* `POST /api/v1/auth/verify-email` — verify an email using the one-time token
  from the email link. Tokens expire after 24 hours.
* `POST /api/v1/auth/verification/resend` — resend a verification link when
  appropriate; the response does not reveal whether the address has an account.
* `POST /api/v1/auth/mfa/login` — complete the pending login with a six-digit
  TOTP code or a one-time recovery code. Challenges expire after five minutes
  and allow at most five attempts.
* `GET /api/v1/auth/mfa/status` — view current authenticator-app MFA status.
* `POST /api/v1/auth/mfa/totp/setup` — start enrollment after confirming the
  current password; returns the secret and `otpauth://` URI once.
* `POST /api/v1/auth/mfa/totp/confirm` — confirm enrollment with a current
  TOTP code and receive ten one-time recovery codes. Save the codes securely.
* `POST /api/v1/auth/mfa/totp/disable` — disable MFA after confirming the
  password and a current TOTP or recovery code.
* `POST /api/v1/travel-clearance-requests` — create a draft clearance request.
* `GET /api/v1/travel-clearance-requests` — list the authenticated user's requests.
* `POST /api/v1/travel-clearance-requests/{id}/submit` — submit a draft for review.
* `GET /api/v1/travel-clearance-requests/pending?page=0&size=20` — page through
  requests awaiting review, oldest first (maximum page size: 100; requires the
  `ROLE_SUPERVISOR` authority).
* `POST /api/v1/travel-clearance-requests/{id}/approve` or `/reject` — supervisor
  decisions (requires the `ROLE_SUPERVISOR` authority).

The frontend supports customer registration, email verification, sign-in, TOTP
enrollment and recovery codes, and personal travel-clearance requests.
TOTP secrets are encrypted at rest using `MFA_ENCRYPTION_KEY_BASE64`;
recovery codes and login-challenge tokens are stored only as SHA-256 hashes.
TOTP time-steps are single-use to block replay, and enrollment challenges are
short-lived and attempt-limited. Passkey/WebAuthn support is planned for a
later phase after the production website origin is established. Production
deployment should also add distributed rate limiting at the edge for public
registration and password-login endpoints. Hibernate uses versioned migrations
and validates the schema at startup. SITA Type
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