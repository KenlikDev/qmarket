# Changelog

## Unreleased

### Added
- Profile API: `GET/PATCH /api/v1/users/me`
- Mock payment: `POST /api/v1/orders/{id}/pay` → status PAID

### Fixed
- Spring Boot 4: use `spring-boot-starter-flyway` (raw `flyway-core` does not auto-configure)
- Schema from Flyway V1+V2; app `ddl-auto=validate`; tests Flyway + `ddl-auto=none`
- ktlint: no wildcard imports; format across server modules

### Added
- `OrderIntegrationTest` — checkout, list, cancel, admin status (Testcontainers)
- Flyway scripts V1 (identity/catalog) + V2 (cart/orders)

### Fixed
- Orders list: no Spring `Pageable`/`sort` binding (Swagger `sort=string` crashed JPA)

### Added
- Module `server/order`: order from cart, stock, cancel, admin status
- Cart API + tests; OpenAPI JWT Authorize
- Auth, Catalog, seed, ktlint, JaCoCo

### Known limitations
- Real PSP payments (mock only)
- Shared KMP client beyond skeleton
