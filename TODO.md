# AeroFlow-Hub Development Roadmap & TODOs

## Phase 1: Core Monolith / Initial Microservice Bootstrap (Current Focus)
Get the foundational Spring Boot service up, running, secured, and processing basic requests.

- [ ] **Project Setup & Database Schema**
    - [ ] Initialize PostgreSQL database (`aeroflow_db`) and verify Hibernate/JPA auto-ddl configurations.
    - [ ] Configure database connection pooling (HikariCP) and environment-specific properties (`application.yml`).
- [ ] **Security & Identity Layer**
    - [ ] Implement Spring Security filter chain with stateless JWT token validation.
    - [ ] Create User, Role, and Permission entity models with Role-Based Access Control (RBAC).
    - [ ] Implement `/api/v1/auth/login` and token generation endpoints.
- [ ] **Core Workflow Engine (State Machine)**
    - [ ] Define the lifecycle states for travel clearances (`DRAFT`, `PENDING_SUPERVISOR`, `PENDING_FINANCE`, `APPROVED`, `REJECTED`).
    - [ ] Integrate a state machine library (or custom transition validator) to handle electronic sign-offs.
    - [ ] Build CRUD REST controllers and service layers for travel clearance requests.
- [ ] **SITA & SSIM Integration Parser (Module 1)**
    - [ ] Write text-stream parsing logic to ingest standard SITA Type B messages.
    - [ ] Build structural parsers for SSIM (Standard Schedules Information Manual) flight schedules.
    - [ ] Implement an automated error quarantine table/queue for malformed or corrupted incoming message streams.
- [ ] **Testing & Quality Assurance**
    - [ ] Write unit tests for workflow state transitions using JUnit 5 and Mockito.
    - [ ] Set up integration tests for REST endpoints and Spring Security configurations.

---

## Phase 2: Microservice Decomposition & Platform Scaling
Transitioning the core system into a scalable, distributed architecture.

- [ ] **Service Discovery & API Gateway**
    - [ ] Implement Spring Cloud Gateway to handle routing, rate limiting, and centralized JWT validation.
    - [ ] Set up a service discovery registry (e.g., Eureka or Consul) for dynamic service registration.
- [ ] **Domain Service Split**
    - [ ] Extract the **Flight Schedule & SITA Ingestion Engine** into an independent reactive microservice.
    - [ ] Extract the **Workflow & Approval Engine** into a dedicated transactional service.
    - [ ] Create a **Notification Service** handling email/SMS alerts (via SendGrid or Africa's Talking integration) for approval milestones.
- [ ] **Asynchronous Event-Driven Architecture**
    - [ ] Introduce Apache Kafka or RabbitMQ as the message broker for inter-service communication (e.g., publishing `TravelRequestSubmittedEvent`).
    - [ ] Configure Redis for distributed caching of flight schedules and user session metadata.

---

## Phase 3: DevOps, Reliability & Observability
Hardening the system for high availability and production deployment.

- [ ] **Containerization & Orchestration**
    - [ ] Write optimized multi-stage `Dockerfile` configurations for each Java service.
    - [ ] Create `docker-compose.yml` for local multi-container development (PostgreSQL, Redis, Kafka, Services).
    - [ ] Build Kubernetes (`k8s`) deployment manifests, Helm charts, and ingress routing rules.
- [ ] **CI/CD Pipeline**
    - [ ] Set up GitHub Actions workflows for automated testing, static analysis (SonarQube), and Docker image building.
    - [ ] Configure automated staging deployments upon merging to the `main` branch.
- [ ] **Observability & Monitoring**
    - [ ] Integrate Micrometer and Prometheus for metrics collection.
    - [ ] Set up Grafana dashboards for JVM performance, database latency, and HTTP request throughput.
    - [ ] Implement centralized log aggregation using ELK (Elasticsearch, Logstash, Kibana) or Grafana Loki.

---

## Phase 4: Frontend Portal & Production Launch
Building the user-facing interfaces and preparing for enterprise deployment.

- [ ] **Web Frontend Portal**
    - [ ] Scaffold a React / Vite or Next.js dashboard for crew members and approvers.
    - [ ] Implement role-based UI views for submitting travel requests, tracking approval progress, and viewing parsed schedules.
- [ ] **Security Auditing & Compliance**
    - [ ] Conduct vulnerability scans on container images and Maven dependencies (OWASP Dependency-Check).
    - [ ] Implement rigorous input sanitization and audit logging for sensitive administrative actions.
- [ ] **Pilot Rollout & Go-Live**
    - [ ] Deploy the platform to a cloud provider (AWS/Azure) production environment.
    - [ ] Onboard pilot airline staff for user acceptance testing (UAT).
    - [ ] Establish a maintenance schedule, database backup routines, and 24/7 uptime monitoring.