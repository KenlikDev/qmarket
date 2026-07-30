# Changelog

## Unreleased

### Added
- `OrderIntegrationTest` — checkout, list, cancel, admin status (Testcontainers)
- Flyway script `V2__cart_and_orders.sql` (not applied yet; Flyway still disabled)

### Fixed
- Orders list: no longer binds Spring `Pageable`/`sort` (Swagger default `sort=string` crashed JPA)

### Added
- Модуль `server/order`: оформление заказа из корзины
  - списание stock, очистка корзины, snapshot позиций
  - отмена PENDING/CONFIRMED с возвратом stock
  - admin: список / статус
- Unit tests `OrderServiceTest`
- Cart API + integration tests
- OpenAPI JWT Authorize
- Auth, Catalog, seed, ktlint, JaCoCo

### Known limitations
- Payments not implemented (status PAID — ручной/админ)
- Flyway disabled (`ddl-auto=update`)
