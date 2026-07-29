# Changelog

## Unreleased

### Added
- OpenAPI/Swagger: JWT Bearer **Authorize** button
- Cart integration tests (`CartIntegrationTest`)
- Модуль `server/cart`: корзина пользователя
  - `GET /api/v1/cart`
  - `POST /api/v1/cart/items`
  - `PUT /api/v1/cart/items/{productId}`
  - `DELETE /api/v1/cart/items/{productId}`
  - `DELETE /api/v1/cart`
- Unit tests for `CartService`
- Auth API, Catalog API
- JWT security, seed data, ktlint, JaCoCo, Docker Compose

### Known limitations
- Schema: Hibernate `ddl-auto=update` (Flyway disabled)
- Orders / Payments not implemented
- KMP clients are starter skeletons only
