# QMarket v0.1 — Modular Monolith Foundation

Kotlin Multiplatform clients + Spring Boot 4.1 modular monolith backend.

**Status:** production-ready foundation for local development and API work.

## What's in v0.1

### Backend (`server`)
| Module | Responsibility |
|--------|----------------|
| `server` | Bootstrap, DataInitializer, config |
| `server/common` | Security, JWT, exceptions, JPA base |
| `server/identity` | Users, roles, register / login / refresh |
| `server/catalog` | Categories & products CRUD + search |

### Features
- JWT access + refresh tokens (Spring Security)
- Admin seed: `admin@qmarket.local` / `admin123`
- Demo catalog: 3 categories, 8 products
- OpenAPI / Swagger UI
- Actuator health
- Unit tests (services + JWT)
- Controller slice tests
- Integration tests (MockMvc + Testcontainers)
- ktlint + JaCoCo

### Clients (starter from KMP template)
Android, iOS, Desktop, Web — skeleton only; API integration starts in later versions.

### Not in v0.1 (next)
Cart, orders, payments, Flyway-as-source-of-truth schema, shared KMP DTOs wired to API, admin UI.

## Quick start

```bash
# 1. PostgreSQL
docker compose up -d

# 2. Server
./gradlew :server:bootRun
```

| URL | |
|-----|--|
| Swagger | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| API base | http://localhost:8080/api/v1 |

### Admin
- Email: `admin@qmarket.local`
- Password: `admin123`

### Useful commands

```bash
# Unit + controller tests (no Docker)
./gradlew :server:common:test :server:identity:test :server:catalog:test

# Integration tests (Docker required)
./gradlew :server:test

# Coverage
./gradlew :server:identity:jacocoTestReport

# Lint
./gradlew :server:ktlintCheck
./gradlew :server:ktlintFormat
```

## Stack
- Kotlin 2.4.x / KMP
- Spring Boot 4.1 / Spring Security 7
- PostgreSQL 17
- Gradle 9.6.1
- JWT (jjwt), SpringDoc OpenAPI 3

## Architecture note
Modular monolith with bounded contexts (`identity`, `catalog`). Evolution path: monolith → clearer module boundaries → extract microservices when needed.
