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

## 🛠️ Getting Started & Setup

### Prerequisites
* JDK 21 installed.
* Maven 3.9+ or Gradle 8+.
* PostgreSQL instance running locally or via Docker.

### 1. Environment Configuration
Configure your `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/aeroflow_db
    username: postgres
    password: yourpassword
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
2. Build and Run
Bash
# Clone repository
git clone [https://github.com/mahingaRodin/AeroFlow-Hub.git](https://github.com/mahingaRodin/AeroFlow-Hub.git)
cd AeroFlow-Hub

# Compile and run spring boot application
mvn clean spring-boot:run