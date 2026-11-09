# AeroFlow-Hub: Current Project Workflow

This document describes what the project currently does and how its main
workflows fit together. It is a snapshot of the implemented system, not a
production-readiness certification or a promise that planned integrations are
already available.

## Project shape

- `be/` is the Java 21 / Spring Boot REST API. It uses PostgreSQL, Flyway,
  Spring Data JPA, Spring Security, JWTs, and Maven.
- `fe/` is the Vue 3 / TypeScript frontend. It currently provides customer
  registration and sign-in, MFA setup, and personal travel-clearance screens.
- PostgreSQL stores users, roles, clearances, audit history, message quarantine,
  airport reference data, and scheduled flights.

## Local application flow

1. Configure PostgreSQL and create the `aeroflow_db` database.
2. Create `be/.env` from `be/.env.example`. Set database credentials, a
   `JWT_SECRET_BASE64`, and a separate `MFA_ENCRYPTION_KEY_BASE64`. Both keys
   must be generated locally and kept out of Git; the MFA key must remain
   stable to decrypt enrolled TOTP secrets.
3. Start the API from `be/` with `mvn spring-boot:run` (port 9092 by default),
   or start the full local stack with Docker Compose. Flyway applies pending
   migrations and Hibernate validates the schema.
4. Start the Vue development server from `fe/` with `npm run dev`. The Vite
   development proxy forwards `/api` requests to the backend on port 9092.

The Docker Compose stack runs PostgreSQL/PostGIS, the API, frontend, and
Mailpit. It is started with `docker compose --env-file be/.env up --build`.
The backend exposes actuator health/readiness endpoints for orchestration.

Required startup secrets are deliberately validated rather than replaced with
insecure defaults. SMTP is used for customer verification email and must be
configured to deliver those messages.

## Customer authentication and account security

1. A customer submits an email and password to `POST /api/v1/auth/register`.
   The backend creates an unverified customer account and emails a verification
   link. Registration responses are generic to avoid disclosing whether an
   email is already registered.
2. The customer verifies the email through `POST /api/v1/auth/verify-email`.
   The account can then sign in using email and password.
3. Password authentication returns a short-lived JWT unless TOTP MFA is
   enabled. When MFA is enabled, login returns a short-lived challenge instead;
   the customer must complete `POST /api/v1/auth/mfa/login` before receiving a
   JWT.
4. Authenticated users can enroll TOTP through the `/api/v1/auth/mfa/totp`
   setup and confirmation endpoints. Recovery codes are issued once and are
   single-use. TOTP secrets are encrypted at rest; challenge tokens and
   recovery codes are stored as hashes.
5. Authentication is stateless. Protected API requests use the JWT bearer
   token. Public auth routes are limited to registration, verification, login,
   and MFA login completion.

The current frontend keeps its access token in browser local storage. Passkeys,
password-reset links, and distributed edge rate limiting are not yet
implemented.

## Travel-clearance workflow

1. An authenticated user creates a clearance request. It starts in `DRAFT`.
2. The requester submits the draft; it becomes `PENDING_SUPERVISOR`.
3. A user with `ROLE_SUPERVISOR` reviews the pending queue and approves or
   rejects the request. Terminal states are `APPROVED` and `REJECTED`; there is
   no transition back from either terminal state.
4. Each creation or state transition is recorded with its actor and timestamp
   in the travel-clearance history. The requester can view their own history;
   supervisors and admins can view a request's history for review.

Relevant API routes are under `/api/v1/travel-clearance-requests`, including the
pending queue, submit, approve/reject, and `/{id}/history` endpoints. The
history table records events from migration V4 onward; it does not reconstruct
older transitions.

## SITA and SSIM message handling

1. An authenticated client submits a text payload to
   `POST /api/v1/ingestion/sita` or `POST /api/v1/ingestion/ssim`.
2. The corresponding parser currently recognizes message/record framing and
   record types, preserves raw content, and reports line-numbered issues.
3. Payloads with parse issues are stored in a time-limited quarantine.
   Supervisors and admins can browse quarantine summaries; only admins can
   retrieve a quarantined raw payload and its issue details.

The parsers do **not** yet extract complete SITA or SSIM business fields or
automatically update flights, bookings, crew assignments, or other operational
records. The quarantine is for review and troubleshooting, not an automated
replay workflow.

## Scheduled flights and QGIS

The current GIS workflow is for **scheduled** flights, not live aircraft
tracking:

1. An admin imports airport reference records through
   `POST /api/v1/operations/airports/import`. Each record includes an IATA code,
   name, country code, and WGS84 latitude/longitude.
2. An admin imports normalized schedule records through
   `POST /api/v1/operations/flights/import`. Each record includes flight number,
   departure and arrival airport codes, ISO-8601 departure/arrival instants,
   and an optional aircraft registration. The airports must already exist.
   Matching flight number/departure-time pairs are updated; new pairs are
   inserted.
3. A supervisor or admin adds the authenticated GeoJSON endpoints as vector
   layers in QGIS:
   - `GET /api/v1/gis/airports` returns airport `Point` features.
   - `GET /api/v1/gis/flights?from=<ISO-8601>&to=<ISO-8601>` returns scheduled
     flight `LineString` features departing in the requested interval.
4. Flight queries are limited to a positive interval of at most 31 days and
   10,000 features. GeoJSON coordinates use longitude, latitude order.

The route lines are direct connections between airport coordinates for map
visualization. They are not actual flight paths, current aircraft positions,
flight status, or a source of estimated arrival times. Schedule imports
currently require normalized JSON; the SSIM parser does not yet convert SSIM
records into these flight records. Use authorized, licensed schedule and
airport-coordinate sources. Do not put bearer tokens in URLs or saved QGIS
project files.

## Roles at a glance

- `ROLE_CUSTOMER`: customer account and its own customer-facing data.
- `ROLE_SUPERVISOR`: review the pending clearance queue, decide requests,
  browse quarantine summaries, and read GIS layers.
- `ROLE_ADMIN`: administrative operations, detailed quarantine access, airport
  and schedule imports, and GIS layer access.

The bootstrap administrator, when explicitly enabled, receives both
`ROLE_ADMIN` and `ROLE_SUPERVISOR`. Disable bootstrap after provisioning.

## What is not currently implemented

- Live aircraft tracking or real-time aircraft locations.
- Parsing SSIM fields into persisted scheduled-flight records.
- Complete SITA/SSIM business-field extraction and downstream workflow actions.
- Live airline inventory, flight shopping, pricing, ticketing, or booking.
- Payment-provider integration and guest booking management.
- Passkey/WebAuthn and password-reset flows.
- Production identity/tenant administration, supplier onboarding, and
  deployment-level rate limiting.

Live tracking, booking, and supplier integrations depend on access to suitable
providers, credentials, licenses, and operational requirements. The existing
GeoJSON endpoints should not be mistaken for those integrations.

## Validation and database changes

Run the backend tests from `be/` with `mvn test`. PostgreSQL integration tests
use Testcontainers and are skipped when Docker is unavailable. The project
keeps schema changes in ordered Flyway migrations; the latest migrations in
this worktree include the clearance audit history (V4), airport/scheduled flight
tables (V5), and PostGIS enablement (V6). Do not edit an already-applied migration to change a
database; add a new migration instead.
