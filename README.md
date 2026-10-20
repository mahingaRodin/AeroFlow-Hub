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
Set `DATABASE_PASSWORD` and a Base64-encoded JWT signing key of at least 256 bits.
For example, generate a key with:

```bash
openssl rand -base64 32
```

Then start the backend from the repository root:

```bash
# PowerShell: set these in the same terminal before starting Spring Boot.
$env:DATABASE_PASSWORD = "your-local-postgres-password"
$env:JWT_SECRET_BASE64 = (openssl rand -base64 32)

cd be
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
B and SSIM parsers are input scaffolds; format-specific extraction remains to
be implemented.