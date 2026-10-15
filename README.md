# WorldPinball Leaderboard API

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)
![Redis](https://img.shields.io/badge/Redis-8-red.svg)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)

High-performance Leaderboard REST API for connected pinball machines in the WorldPinball ecosystem.  
Java Spring Boot implementation of the leaderboard service.

---

## Architecture & Structure

```
com.worldpinball.leaderboard
├── api/                  # REST Controllers, DTOs & Global Exception Handler (RFC 7807)
├── application/          # Use cases, Business Services & Application DTOs
├── domain/               # Core JPA Entities (ScoreEntry, Machine, Device, GameSession...)
└── infrastructure/       # Spring Data JPA Repositories & PostgreSQL persistence
```

### Key Technical Decisions
- **Append-Only Auditability**: Scores are never overwritten in place (`score_entries`). An invalidation mechanism flags fraudulent entries without breaking historical audit trails.
- **Optimized Composite Indexing**: `(ip_id, mode_id, score, achieved_at_utc)` covers filtering and ordering without in-memory filesort.
- **Decoupled DTO Layer**: Strict separation between JPA entities and API contracts to prevent serialization issues and avoid N+1 queries.
- **Isolated Environment**: Dedicated Docker configuration on distinct ports (PostgreSQL `5433`, Redis `6380`) to prevent port collisions with other local services.

---

## Getting Started

### Prerequisites
- JDK 21
- Docker & Docker Compose

### 1. Start Infrastructure
Copy the environment template and run the containers:
```bash
cp .env.example .env
docker compose up -d
```

### 2. Run the Application
```bash
./mvnw clean spring-boot:run
```

### 3. API Documentation
Swagger UI is available at:  
http://localhost:8080/swagger-ui.html

---

## REST Endpoints Overview

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/leaderboards/{ipId}/{modeId}?limit=10` | Retrieve Top N leaderboard rankings |
| `POST` | `/api/v1/leaderboards/scores` | Record an achieved game score |
| `GET` | `/api/v1/leaderboards/{ipId}/{modeId}/rank?score=X` | Calculate dynamic player rank for a given score |

---

## Testing

Run unit and integration tests:
```bash
./mvnw test
```
