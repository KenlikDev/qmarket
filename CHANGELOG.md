# Changelog

## Unreleased

### Fixed
- Stock overselling: atomic `UPDATE … WHERE stock >= qty` (no JPA `@Version`)
- Drop orphan `products.version` column (Flyway V5); was unused after stock strategy change
- Address entity: `default` → `isDefault` (SQL/JPQL reserved word broke context startup)
- Seed gated by `qmarket.seed.enabled` (off under `prod` profile)
- CORS origins from config (no wildcard `*`)

### Added
- `StockConcurrencyTest` — parallel decreaseStock never oversells
- `server/Dockerfile` multi-stage bootJar image
- `application-prod.yml` seed/CORS defaults

### Added
- Shared Compose UI: login, catalog, cart, checkout, my orders, mock pay
- Ktor ApiClient: profile, addresses, cart mutate, orders
- Ktor `QMarketApiClient` in app:shared (login/register, products, cart) + MockEngine tests
- Shared KMP API DTOs in `:core` (auth, catalog, cart, order, address, error) + Json config
- Order aggregate (cancel/markPaid/applyAdminStatus); repos return Kotlin nullable
- Cart aggregate methods (addItem/changeQuantity/removeItem); CartRepository returns `Cart?`
- `server:catalog-api` + `ProductCatalog` port; cart/order no longer depend on catalog JPA
- ArchUnit rules for module boundaries
- Checkout: optional `addressId` on create order (address book → shippingAddress)
- Shipping addresses CRUD (`/api/v1/users/me/addresses`) + Flyway V3
- Change password: `POST /api/v1/users/me/password`
- Catalog filters: minPrice, maxPrice, sortBy, sortDir
- Profile API: `GET/PATCH /api/v1/users/me`
- Mock payment: `POST /api/v1/orders/{id}/pay` → status PAID

### Fixed
- Gradle 10: `kotlin.version` in gradle.properties; drop unused server→`:core` dep (KMP client only)
- Integration tests: Jackson TestJson instead of Regex for tokens/ids; drop unused @Testcontainers
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
