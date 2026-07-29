# Changelog

## [0.1.0] — 2026-07-29

### Added
- Modular monolith server: `common`, `identity`, `catalog`
- Auth API: register, login, refresh (JWT)
- Catalog API: categories & products (list, get, create, search)
- Spring Security + JWT filter
- Global exception handler
- DataInitializer: roles, admin user, demo catalog
- Unit tests (AuthService, CatalogService, JwtService)
- Controller slice tests (Auth, Product)
- Integration tests (Auth, Catalog) with Testcontainers
- ktlint, JaCoCo
- Docker Compose for PostgreSQL
- Swagger UI / Actuator

### Notes
- Schema managed by Hibernate `ddl-auto=update` for v0.1; Flyway scripts present but disabled until next iteration
- KMP client apps are template skeletons only
