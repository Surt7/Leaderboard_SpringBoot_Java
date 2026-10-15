# WorldPinball Leaderboard API 🎯

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)
![Redis](https://img.shields.io/badge/Redis-8-red.svg)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)

High-performance, event-driven Leaderboard REST API for connected pinball machines (**WorldPinball** ecosystem).  
Faithful Java Spring Boot mirror of the .NET/C# ASP.NET Core reference service.

---

## 🏗️ Architecture & Key Concepts

```
com.worldpinball.leaderboard
├── api/                  # REST Controllers, DTOs & Global Exception Handler (RFC 7807)
├── application/          # Use cases, Business Services & Application DTOs
├── domain/               # Core JPA Entities (ScoreEntry, Machine, Device, GameSession...)
└── infrastructure/       # Spring Data JPA Repositories & PostgreSQL persistence
```

### Highlights for Tech Interviews
- **Append-Only Auditability** : Scores are never overwritten in place (`score_entries`). An invalidation method flags fraudulent entries without breaking historical audit trails.
- **Optimized Composite Index** : `(ip_id, mode_id, score, achieved_at_utc)` covers both filtering and ordering without in-memory filesort.
- **Proxy-Safe DTOs** : Strict decoupling between JPA Hibernate entities and presentation DTOs, avoiding proxy serialization issues and N+1 lazy loading overhead.
- **Container Isolation** : Fully isolated Docker environment on custom ports (PostgreSQL `5433`, Redis `6380`) guaranteeing zero collision with sister microservices.

---

## 🚀 Quick Start

### 1. Prerequisites
- **JDK 21**
- **Docker & Docker Compose**

### 2. Start Infrastructure
Copy the environment variables template and start the containers:
```bash
cp .env.example .env
docker compose up -d
```

### 3. Run the Application
```bash
./mvnw clean spring-boot:run
```

### 4. Interactive API Documentation (Swagger UI)
Once started, explore and test the endpoints directly from your browser:
👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

---

## 📡 REST Endpoints Overview

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/leaderboards/{ipId}/{modeId}?limit=10` | Retrieve Top N leaderboard rankings |
| `POST` | `/api/v1/leaderboards/scores` | Record an achieved game score (with `@Valid` checks) |
| `GET` | `/api/v1/leaderboards/{ipId}/{modeId}/rank?score=X` | Calculate dynamic player rank for a given score |

---

## 🧪 Testing

Run unit and integration tests against real PostgreSQL test instance:
```bash
./mvnw test
```
