# AeroFlow-Hub: Roadmap and Build Reference

Companion to *"AeroFlow-Hub: Current Project Workflow"*. That document says what exists today. This one says what to build next, in what order, and why.

Suggested location: `docs/ROADMAP.md`. Update the checkboxes as work lands, and move finished phases into the workflow doc.

> **Scope note.** This roadmap targets *ground/operations-side* software (flight ops, schedules, maintenance records, crew, safety). Nothing here is certified for operational or safety-critical use. Treat every output as advisory until validated by the operator's accountable managers and the applicable regulator. Airborne software (DO-178C) is a different world and out of scope.

---

## 1. Where we are

| Area | Today | Gap |
|---|---|---|
| Auth | JWT, TOTP MFA, recovery codes, email verification | No refresh tokens, no password reset, no passkeys, token in `localStorage`, no rate limiting |
| Clearance workflow | DRAFT → PENDING_SUPERVISOR → APPROVED/REJECTED with history | Not tied to any real operational object (flight, crew, aircraft) |
| SITA / SSIM | Framing + record-type parsing, quarantine | No field extraction, nothing flows into flights |
| Flights | Manually imported *schedule* records, GeoJSON for QGIS | No status, no events, no live position, no delays |
| Airports | Code, name, country, lat/lon | No ICAO code, timezone, runways, elevation, hours |
| Frontend | Customer login, MFA, clearance screens | No ops UI at all (supervisor/admin work is API/QGIS only) |
| Ops domains | None | Maintenance, crew, weather/NOTAM, safety, ground handling, baggage all absent |

**Biggest structural gap:** the project has *pipes* (ingestion, quarantine) and *a map*, but no **operational core**: a flight as a living object with a status, a timeline of events, and an aircraft and crew attached. Almost everything valuable in aviation software hangs off that object, so build it first.

---

## 2. Guiding principles

1. **Flight as the aggregate root.** Everything (delays, crew, maintenance holds, messages, positions) attaches to a flight leg or an aircraft.
2. **Event-driven, append-only.** Store what was *reported* (with source and timestamp), derive current state from it. Aviation data is late, duplicated and contradictory; you need history to reconcile it.
3. **UTC everywhere internally.** Store IANA timezone on airports and convert only at the UI edge. Most bugs in aviation scheduling are timezone/DST bugs.
4. **Keep raw + parsed.** Never discard the original message. Reparse when your parser improves (you already do this for quarantine, so extend the idea).
5. **Idempotent ingestion.** The same message arriving twice must not create two events.
6. **Explainable before clever.** Rule-based and auditable first, ML second. Regulators and ops staff trust what they can trace.
7. **Standards over custom formats.** Map to IATA/ICAO/AIXM/ARINC vocabularies from day one, even if you only support a subset.
8. **Licensing awareness.** Schedule data (OAG, Cirium), airport data (OurAirports is open; others are not), and ADS-B feeds each have terms. Record the license of every data source in `docs/DATA_SOURCES.md`.

---

## 3. Pick a wedge (decide before Phase 2)

"Everything for aviation" will not ship. Choose one primary story and let the rest support it:

| Wedge | Who it serves | Why it's plausible |
|---|---|---|
| **A. Operations control for small/regional carriers** | Ops controllers, dispatchers | Small operators often run on spreadsheets, WhatsApp and legacy tools; a lightweight OCC (operations control centre) view is valuable |
| **B. Messaging gateway + normalizer** | Airlines/airports with legacy Type B/SSIM feeds | Your SITA/SSIM work is already here; becomes "legacy messages → clean API/events" |
| **C. Maintenance and airworthiness tracker** | Small operators / CAMO / MROs | Tracking AD/SB compliance, component life and defects is painful and regulated |
| **D. Safety management (SMS) platform** | Any certificated operator | Required by ICAO Annex 19; many operators use weak tools |

Recommendation: **B → A**. Finish the ingestion pipeline (Phase 1), then put an ops-control UI on top (Phase 2). C and D are natural later modules because they share the aircraft/flight entities.

> Decision: _______ (fill in)  Date: _______

---

## 4. Roadmap

Priority tags: **P0** = do first, **P1** = core value, **P2** = differentiator, **P3** = later/optional.

### Phase 0: Hardening and foundations (P0, 2–4 weeks)

Fix known gaps before adding domains; they get more expensive later.

**Security**
- [ ] Move access token out of `localStorage`. Use short-lived access token in memory plus `HttpOnly; Secure; SameSite` refresh cookie, with refresh-token rotation and reuse detection.
- [ ] Password reset flow (single-use, hashed, expiring tokens; generic responses).
- [ ] Rate limiting and lockout on login, MFA, registration, verify-email (Bucket4j or at the gateway). Add per-IP and per-account limits.
- [ ] Scoped machine credentials for QGIS and ingestion clients (API keys or OAuth2 client-credentials with scopes such as `gis:read`, `ingest:write`). This removes the need to hand a user JWT to QGIS and avoids tokens in project files.
- [ ] Passkeys/WebAuthn (P1, after the above).
- [ ] Move from role-only to **role + scope + resource** checks (ABAC-style) as soon as multi-tenant or per-station access appears.
- [ ] Security headers, CORS allow-list, dependency scanning (OWASP Dependency-Check or Dependabot), secrets scanning in CI.
- [ ] Audit log generalized beyond clearances: who read/changed what, from where.

**Engineering**
- [x] CI pipeline configured: Maven verification (including Testcontainers/migrations on the Docker-enabled runner) and frontend typecheck/build.
- [ ] OpenAPI spec generated (springdoc) and published; version the API (`/api/v1` is good, define deprecation policy).
- [ ] Structured JSON logging, correlation IDs, Micrometer metrics, health/readiness endpoints, OpenTelemetry traces. (Actuator health/readiness endpoints are available.)
- [ ] Standard error format (RFC 9457 Problem Details).
- [x] Docker Compose for the full local stack (Postgres + PostGIS, backend, frontend, Mailpit for SMTP).
- [x] Switch Postgres to **PostGIS** now; spatial queries are coming (Phase 3).

**Acceptance:** a new developer can clone, run one command, and have a working stack plus green CI.

---

### Phase 1: Real message ingestion (P0/P1, 4–6 weeks)

Turns the quarantine pipeline into a real data source.

**1.1 Finish SSIM → schedule**
- [ ] Parse SSIM Chapter 7 record types: header (1), carrier (2), flight leg (3), segment data (4), trailer (5). Extract carrier, flight number, operational suffix, leg dates and days-of-operation bitmap, STD/STA, UTC offsets, aircraft type, service type, etc.
- [ ] Expand period + days-of-operation into dated **flight instances**; handle date-variation across UTC offsets.
- [ ] Map into the existing `flights` import path (single code path for JSON import and SSIM).
- [ ] Diff import: "what changed since last schedule" (added/cancelled/retimed), not just upsert.

**1.2 SITA Type B / operational messages** (pick by value)
- [ ] **MVT** (movement: AD actual departure, AA arrival, ED/EA estimates, DL delays with IATA delay codes, RR return to ramp, FR forced return).
- [ ] **ASM/SSM** (ad-hoc/standard schedule changes: NEW, CNL, RPL, TIM, EQT, RTM).
- [ ] **LDM / PSM / PTM / BSM / BTM** (load, passenger/baggage messages). Lower priority; start with LDM.
- [ ] **PNL/ADL** (passenger name lists) only with strong privacy controls; treat as personal data.
- [ ] Address line + originator + timestamp parsing (the `QD`/`.` lines), correct handling of day-of-month with no month/year (resolve against received time).

**1.3 Pipeline quality**
- [ ] Message table with: raw, type, source, received_at, parsed_at, parser_version, status, dedupe hash.
- [ ] **Reparse** endpoint/job for a message or batch when parser version changes.
- [ ] Idempotency keys, dead-letter handling, quarantine-to-resolved workflow (admin edits/ignores/reprocesses; record the decision).
- [ ] Transport adapters beyond HTTP POST: SFTP drop folder, IMAP mailbox, and later AMQP/MQ. Real feeds rarely arrive by REST.
- [ ] Golden-file test suite: real-looking (sanitized) sample messages per type, including malformed ones.

**Acceptance:** a sample SSIM file and a stream of MVT messages update flight instances and a flight event timeline with no manual JSON import.

---

### Phase 2: Operational core (P1, 6–8 weeks)

The heart of the product.

**2.1 Domain model**
- [ ] `aircraft` (registration, ICAO type, MSN, operator, seats/config, status)
- [ ] `flight` (schedule identity) vs `flight_instance` / `leg` (a dated operation) vs `flight_event` (append-only: scheduled, estimated, actual OOOI times, status changes, delay events, source, received_at)
- [ ] Extend `airport`: ICAO code, IANA timezone, elevation, runways (length/surface/heading), operating hours, country
- [ ] Reference data: aircraft types (ICAO designators), airlines (IATA/ICAO), **IATA delay codes (AHM 730 / 2-digit and alphanumeric)**

**2.2 Flight status state machine**
- [ ] States such as `SCHEDULED → BOARDING → DEPARTED (OFF-BLOCK) → AIRBORNE → LANDED → ARRIVED (ON-BLOCK)`, plus `DELAYED`, `CANCELLED`, `DIVERTED`, `RETURNED`.
- [ ] Track **OOOI** times (Out, Off, On, In) and derive block time, taxi-out/in, air time.
- [ ] Rule: status is *derived from events*, with source priority (e.g. ACARS/MVT > ADS-B inference > manual) and conflict logging.

**2.3 Delay management**
- [ ] Capture delay code, minutes, responsible party, free-text.
- [ ] Reactionary delay chains (inbound aircraft late → outbound late). This is one of the most useful ops views.
- [ ] Minimum turnaround time (MTT) checks per aircraft type/airport.

**2.4 Airport Collaborative Decision Making (A-CDM) milestones** (P2)
- [ ] Model key milestones: ELDT, TLDT, AIBT, TOBT, TSAT, TTOT, AOBT. Even a partial model makes you speak the language of airports.

**2.5 Operations UI (Vue)**
- [ ] Ops board (Gantt-style by aircraft/tail, and a FIDS-style departures/arrivals board).
- [ ] Flight detail with event timeline, messages, delay info.
- [ ] Live updates via **Server-Sent Events or WebSocket** (SSE is simpler and plenty for a first version).
- [ ] Supervisor/admin screens for quarantine review, imports, user/role admin. Right now these are API-only.

**Acceptance:** an ops controller can watch a day of flights progress, see delays cascade across a tail number, and drill into the messages that caused each change.

---

### Phase 3: Live tracking, weather and notices (P1/P2, 4–6 weeks)

Adds "what is actually happening" to the schedule view.

**3.1 Live positions**
- [ ] Ingest ADS-B from a provider or your own receiver. Candidate sources: OpenSky Network, ADS-B Exchange, commercial APIs; **check current terms, rate limits and commercial-use rules for each before building on them**.
- [ ] **Coverage caveat:** ADS-B/MLAT coverage is patchy in parts of Africa and over oceans. Consider a local RTL-SDR/ADS-B receiver for airport-vicinity coverage, and design for gaps.
- [ ] Table design: `position_report(aircraft, ts, lat, lon, alt, gs, track, vrate, source)` with PostGIS geometry, partitioned by time (or TimescaleDB). Keep retention policy.
- [ ] Match positions to flights (callsign/hex-to-registration + schedule window). This matching is harder than it looks; log ambiguities.
- [ ] Infer events from positions (takeoff/landing by altitude/ground speed/airport geofence) as a *low-priority* source.
- [ ] Live map layer (MapLibre GL JS or Leaflet) plus a QGIS live layer for GIS users. Replace "direct line between airports" with great-circle and, later, actual track.

**3.2 Weather**
- [ ] METAR/TAF/SIGMET ingestion (NOAA Aviation Weather Center and national met services; confirm terms). Parse flight category (VFR/MVFR/IFR/LIFR), crosswinds vs runway heading.
- [ ] Weather overlay on map and airport pages; alert rules (visibility, thunderstorm, crosswind limits).

**3.3 NOTAMs**
- [ ] Ingest NOTAMs for departure/arrival/alternate airports; classify (runway closure, navaid U/S, airspace restrictions). Availability varies by country and provider; Digital NOTAM (AIXM 5.1) is the long-term standard.

**3.4 Alerts engine**
- [ ] Generic rules → notifications (in-app, email, SMS/WhatsApp/Telegram optional). Examples: "arrival delayed > 30 min", "alternate airport below minima", "aircraft with open MEL item scheduled on ETOPS leg".

**Acceptance:** live aircraft on a map linked to their scheduled flight, with weather/NOTAM context and alert rules.

---

### Phase 4: Maintenance and airworthiness (P1/P2, 8–12 weeks)

A major gap in lightweight tools, and heavily regulated, so scope carefully.

- [ ] **Aircraft technical log / defect reporting** (pilot-reported defects, rectification, deferral).
- [ ] **MEL/CDL deferrals**: item, category (A/B/C/D), deferral date, expiry, operational (O) and maintenance (M) procedures; countdown and alerts.
- [ ] **Airworthiness Directives and Service Bulletins**: applicability, compliance status, due dates (calendar, flight hours, cycles).
- [ ] **Component tracking** (life-limited parts, time since new/overhaul, installed position, ATA chapter references).
- [ ] **Utilization tracking**: flight hours, cycles, landings, derived from flight events (this is where Phase 2 pays off).
- [ ] **Maintenance planning**: scheduled checks (A/B/C/D or equivalent MPD tasks), hangar slots, forecast due-dates, conflict with flight schedule (aircraft assignments).
- [ ] **Work orders and task cards**, sign-offs with authorization checks (certifying staff, licence categories). Digital signature and immutable audit trail.
- [ ] Parts/inventory (P3).
- [ ] Standards to be aware of: ATA iSpec 2200 / S1000D (documentation), EASA Part-M/Part-145/CAMO or FAA Part 43/91/121/135 depending on jurisdiction. **Confirm the target regulatory regime before modelling sign-off rules.**
- [ ] Integrations later: ACMS/AHM data, engine health monitoring files.

**Acceptance:** for any tail, show "what is due, when, and what is open" and prevent scheduling an aircraft that is not airworthy under recorded constraints (as an advisory check).

---

### Phase 5: Crew management (P2, 8–12 weeks)

- [ ] Crew master data: licences, ratings, type qualifications, medicals, recency, passport/visa expiry.
- [ ] Crew pairing/rostering model: duties, rest, standby, leave.
- [ ] **Flight Time Limitations (FTL) engine**: configurable rule sets (EASA ORO.FTL, FAA Part 117, or local CAA rules). Rules are dense and jurisdiction-specific; isolate them as data/config with heavy tests.
- [ ] Legality checks on schedule changes ("if this flight slips 90 minutes, who goes illegal?").
- [ ] Crew notifications and acknowledgements.
- [ ] Optimization (later): pairing/rostering with a solver (OR-Tools), only after rule checks are trustworthy.
- [ ] Fatigue risk indicators (feeds into Phase 6).

---

### Phase 6: Safety Management System (P2, 6–8 weeks)

Aligned with ICAO Annex 19 / Doc 9859 concepts.

- [ ] Occurrence/hazard reporting (including voluntary and confidential reporting with careful anonymity design).
- [ ] Risk assessment matrix (severity × likelihood), mitigations, owners, due dates, effectiveness review.
- [ ] Investigation workflow, root-cause taxonomy (ECCAIRS/ADREP categories where applicable).
- [ ] Safety performance indicators and dashboards; trend analysis.
- [ ] Document control and policy acknowledgement.
- [ ] Link occurrences to flights, aircraft, crew, weather, maintenance records (value of the shared data model).

---

### Phase 7: Station and ground operations (P2/P3)

- [ ] Turnaround task tracking (fueling, catering, cleaning, boarding, baggage), with timestamps for A-CDM.
- [ ] Ground handler task assignment, mobile-friendly PWA for ramp staff (offline-tolerant; connectivity at stations is unreliable).
- [ ] Gate/stand allocation with conflict detection.
- [ ] Baggage events (BSM/BTM/BPM concepts, IATA Resolution 753 tracking milestones).
- [ ] Ground-support equipment and fuel records.

---

### Phase 8: Passenger-facing and commercial (P3, only if wedge requires it)

Currently out of scope in the workflow doc; keep it that way until the ops core is strong.

- [ ] Booking/PNR model, fares, inventory (large domain; consider integrating rather than building).
- [ ] NDC (IATA New Distribution Capability) / ONE Order concepts for offers and orders.
- [ ] Payments via a PCI-compliant provider (never store card data yourself).
- [ ] Disruption handling: rebooking, passenger notifications, EU261-style compensation calculators (jurisdiction-specific).
- [ ] Privacy: GDPR/local data protection law, APIS/PNR government reporting obligations.

---

### Phase 9: Analytics and AI (P2 once data exists)

Only start when Phases 1–3 have produced a few months of clean data.

- [ ] **Data warehouse layer**: read replica or analytics schema, daily snapshots.
- [ ] **KPIs**: on-time performance (D0/D15), completion factor, average delay by cause, turnaround time, utilization, schedule adherence.
- [ ] **Delay prediction**: features such as time of day, weather, inbound delay, airport congestion; start with gradient boosting, evaluate against a naive baseline (e.g. "same as inbound delay"). Report calibration, not just accuracy.
- [ ] **Predictive maintenance**: needs real sensor/fault data; do not attempt on schedule data alone.
- [ ] **Anomaly detection** on message feeds and positions (e.g. missing MVT, impossible times).
- [ ] **LLM-assisted features** (with guardrails): natural-language ops summaries, message explanation, draft shift-handover reports. Keep a human in the loop, show sources, never let a model change operational records unreviewed.
- [ ] Reporting exports (CSV/Parquet), scheduled reports.

---

### Phase 10: Production readiness and compliance (continuous, P1 before any real customer)

- [ ] Environments: dev / staging / prod, infrastructure as code (Terraform), container images, Kubernetes or a managed container service.
- [ ] Backups with tested restores, defined RPO/RTO, disaster recovery plan.
- [ ] High availability for the database and message broker; zero-downtime migrations (expand/contract pattern with Flyway).
- [ ] Secrets in a manager (Vault / cloud KMS), key rotation plan for JWT and MFA keys (support multiple active key IDs).
- [ ] **Multi-tenancy** decision: row-level `tenant_id` with Postgres RLS vs schema-per-tenant. Decide before onboarding a second customer.
- [ ] Penetration test and threat model (STRIDE) for the API and ingestion endpoints.
- [ ] Compliance mapping: ISO 27001 / SOC 2 as business needs; aviation cyber guidance such as **EASA Part-IS** (information security for aviation organisations; check applicability dates and scope for your market), data-protection law for your jurisdictions.
- [ ] Load and soak tests, especially ingestion bursts and SSE fan-out.
- [ ] SLAs, status page, incident runbooks.

---

## 5. Cross-cutting technical decisions to make early

| Decision | Options | Lean |
|---|---|---|
| Event backbone | Postgres outbox only / Kafka / RabbitMQ | **Start with Postgres outbox + Spring events**; adopt Kafka/RabbitMQ when throughput or multiple consumers demand it |
| Time-series positions | Plain Postgres partitions / TimescaleDB | TimescaleDB extension on Postgres is pragmatic |
| Spatial | PostGIS | Yes, now |
| Real-time to UI | SSE / WebSocket | SSE first |
| Frontend maps | MapLibre GL JS / Leaflet | MapLibre for large layers |
| Background jobs | Spring `@Scheduled` / Quartz / JobRunr | JobRunr or Quartz once jobs need persistence and retry |
| Architecture | Modular monolith / microservices | **Modular monolith** with clear package boundaries (`ingestion`, `ops`, `maintenance`, `crew`, `safety`, `identity`); split only for proven reasons |
| API style | REST + OpenAPI / GraphQL | REST; consider GraphQL only for complex ops-board queries |
| Search | Postgres FTS / OpenSearch | Postgres first |
| Caching | None / Redis | Add when measured |

Backend package layout suggestion:

```
be/src/main/java/.../
  identity/      (users, roles, MFA, tokens)
  ingestion/     (SITA, SSIM, adapters, quarantine, reparse)
  ops/           (aircraft, flights, events, delays, status engine)
  tracking/      (positions, matching, geofences)
  weather/       (METAR/TAF/NOTAM)
  maintenance/
  crew/
  safety/
  clearance/     (existing travel-clearance workflow)
  gis/
  platform/      (audit, notifications, alerts, config)
```

---

## 6. Standards and data cheat-sheet

| Name | What it is | Used for |
|---|---|---|
| IATA SSIM | Standard Schedules Information Manual (Ch. 7 = fixed-width schedule file) | Schedules |
| IATA Type B / SITA messages | MVT, LDM, PSM, BSM, PNL, ADL, ASM, SSM, etc. | Ops messaging |
| IATA AIDX | XML standard for flight data exchange | Modern flight data APIs |
| IATA AHM 730/732 | Delay codes and airport handling references | Delay analysis |
| ICAO Doc 4444 | Flight plan formats (FPL, DLA, CHG, CNL, DEP, ARR) | ATC messaging |
| ICAO Annex 19 / Doc 9859 | Safety management | SMS |
| ICAO Doc 8643 | Aircraft type designators | Reference data |
| ARINC 424 / AIXM 5.1 | Navigation / aeronautical information exchange | Airports, airways, NOTAM digital |
| ARINC 429/664/ACARS | Avionics buses and datalink | Aircraft data (mostly out of scope for ground apps) |
| ASTERIX | Surveillance data (ATC radar/ADS-B) | Only if integrating ATC surveillance |
| FF-ICE | Future flight information exchange | Long-term ATM trend |
| METAR / TAF / SIGMET | Weather reports/forecasts | Weather |
| A-CDM | Airport collaborative decision making milestones | Turnaround/ATFM |
| ATA iSpec 2200 / S1000D | Maintenance documentation standards | Maintenance |

Reference data sources to evaluate (verify license before use): OurAirports, OpenFlights, ICAO/IATA code lists, NOAA AWC, OpenSky Network, ADS-B Exchange, national AIS/AIP publications.

---

## 7. Testing strategy

- **Unit:** parsers (table-driven tests with real-format samples), state machine transitions, FTL rules, MEL expiry calculations.
- **Property-based:** timezone/date expansion in SSIM (jqwik) and "message order shouldn't change final state if events carry timestamps".
- **Integration:** Testcontainers (Postgres/PostGIS), message ingestion end-to-end.
- **Contract:** OpenAPI-based tests between `fe/` and `be/`.
- **Replay tests:** feed a recorded day of messages and assert the resulting flight timeline.
- **Security:** authz matrix tests (each role × each endpoint), dependency and container scans.
- **Performance:** ingestion burst and live-map fan-out.

---

## 8. Suggested next 30 days

| Week | Focus | Output |
|---|---|---|
| 1 | Phase 0 security quick wins | Refresh-token cookie flow, rate limiting, scoped API keys for GIS/ingestion |
| 1–2 | CI + Docker Compose + PostGIS + OpenAPI | One-command local stack, green pipeline |
| 2–3 | SSIM field extraction + flight instance expansion | Schedule file → flight instances |
| 3–4 | `flight_event` table + MVT parser + status derivation | First live-ish timeline for a flight |
| 4 | Minimal ops board in Vue | List + detail view with timeline |

Definition of done for the month: *upload an SSIM file and send a few MVT messages; see flights and their changing status in the UI.*

---

## 9. Non-goals (for now)

- Replacing a full airline reservation system or DCS.
- Anything that directly controls aircraft or air traffic.
- Claiming regulatory approval or operational certification.
- Selling predictions as guarantees (delay ETAs are estimates; state confidence).

---

## 10. Open questions / decision log

| # | Question | Decision | Date |
|---|---|---|---|
| 1 | Primary wedge (A/B/C/D)? | | |
| 2 | Target region and regulator (EASA/FAA/local CAA)? | | |
| 3 | Who are the first 3 real users to interview? | | |
| 4 | Schedule data source and license? | | |
| 5 | Live tracking source and license? | | |
| 6 | Multi-tenant from the start? | | |
| 7 | Hosting/cloud and data-residency requirements? | | |

**Highest-value action outside the code:** talk to 3–5 dispatchers, ops controllers or maintenance planners and watch how they work today. Their pain points should reorder this roadmap.
