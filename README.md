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

## Getting Started

### Prerequisites
* JDK 21.
* Maven 3.9+.
* PostgreSQL with an `aeroflow_db` database.

### Configure and run
Set the database password and a Base64-encoded JWT signing key of at least 256 bits.
For example, generate a signing key with:

```bash
openssl rand -base64 32
```

Set `DATABASE_PASSWORD` and `JWT_SECRET_BASE64` in the environment, then start the
service:

```bash
mvn spring-boot:run
```

The service listens on port `8080` by default. Database URL and username can be
overridden with `DATABASE_URL` and `DATABASE_USERNAME`. Hibernate schema update
is enabled for this bootstrap; use versioned database migrations before
production deployment.

### Initial API
* `POST /api/v1/auth/login` — authenticate with a provisioned username/password
  and receive a bearer token.
* `POST /api/v1/travel-clearance-requests` — create a draft clearance request.
* `GET /api/v1/travel-clearance-requests` — list the authenticated user's requests.
* `POST /api/v1/travel-clearance-requests/{id}/submit` — submit a draft for review.
* `POST /api/v1/travel-clearance-requests/{id}/approve` or `/reject` — supervisor
  decisions (requires the `ROLE_SUPERVISOR` authority).

The SITA Type B and SSIM parser components currently preserve message lines as
input scaffolding; format-specific record validation and extraction remain to
be implemented. User and role provisioning is also intentionally left to the
deployment/bootstrap process.