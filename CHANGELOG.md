### Fixed / hardened (ChatGPT review P1)
- Cart get-or-create: UNIQUE(user_id) conflict → re-read (no double cart)
- Login rate limiter: optional client IP key, bounded map, injectable Clock
- Actuator metrics/prometheus require ADMIN/MANAGER (health/info public)
- Concurrent cancel↔pay / double-cancel integration tests (optimistic lock)

### Security / correctness (P0 from review)
- Public `ProductResponse` does not expose `costPrice` (write-only on create/update)
- OpenAPI description no longer embeds seed admin password; seed log omits password
- `markAllRead` uses bulk SQL UPDATE (not first 200 rows)
- Optimistic locking: `orders.version` / `products.version` (Flyway V10); stock native updates bump version
- Concurrent state conflicts map to HTTP 409
- Public get product by id/slug hides inactive products (404)
- Product create normalizes slug/SKU before uniqueness checks
- `application-prod.yml`: required DB credentials, quieter logging, Flyway baseline-on-migrate false


### Fixed
- Checkout Idempotency-Key: request fingerprint; same key + different body → 409 (V8)
- Order lifecycle: strict status transitions; admin CANCELLED restocks inventory
- cancelMyOrder: status transition before restock (fail-fast, no double-restock path)
- Order.items fetch LAZY (access within @Transactional)
- Seed off by default (`QMARKET_SEED_ENABLED=true` for local); prod JWT secret required

### Added
- Admin: assign product category on create/edit
- Admin category edit; OrderIntegrationTest for idempotency body mismatch (409)
- Admin product update/delete UI + `UpdateProductRequestDto` / client methods

# Changelog

## Unreleased

### Refactor
- Client composition root: extract `QMarketAppModel` (session, catalog/cart/orders, admin, checkout); `App.kt` is screen wiring only (~460 lines)

### Security
- Prod locks down OpenAPI (`qmarket.security.api-docs-public`, springdoc disabled) and narrows actuator exposure
- `JwtProperties` rejects compose/dev placeholder secrets; docker-compose requires `JWT_SECRET` via env
- iOS session store: Keychain (`kSecClassGenericPassword`, AfterFirstUnlockThisDeviceOnly) via CFDictionary/CFData (KN 2.4)



### Security
- iOS session store: Keychain (`kSecClassGenericPassword`, AfterFirstUnlockThisDeviceOnly) with one-shot migration from NSUserDefaults
- Android parity already in place (EncryptedSharedPreferences AES-256)


### Added
- Admin order status transitions emit customer in-app notifications
- TopBar **Alerts** entry with unread badge; catalog load refreshes count


### Added
- In-app notifications: Flyway V9, order lifecycle events, REST API, shared UI (Profile → Notifications)


### Fixed
- Checkout double-submit: reuse pending Idempotency-Key until success; lock concurrent taps; invalidate key when cart/address changes


### Added
- Order detail screen: line items, shipping, pay/cancel/refresh via `getOrder`


### Added
- Shopper catalog category filter wired to `listProducts(categoryId)`
- Product detail quantity selector (−/+) up to stock


### Added
- Product detail screen: open from catalog, `getProduct` / `getProductBySlug` on API client


### Fixed
- Product DELETE is soft-delete (active=false) so cancel can still restock
- Password change revokes all refresh-token sessions
- Concurrent refresh loser no longer revokes the winner family
- Idempotency race only handles UNIQUE(user_id,idem_key), not all DataIntegrity violations

### Fixed
- Session switch: clear Ktor Bearer token cache on login/register/logout (previously requests kept the previous user JWT)
- Clear user-scoped UI state (orders, profile fields, cart, addresses) on account switch

### Added
- `application-dev.yml`: seed enabled only with profile `dev` (default and prod stay off)
- : seed on only with profile  (default/prod stay off)
- Refresh token rotation: server-side jti (Flyway V7), family reuse detection, POST /auth/logout
- Checkout idempotency: Idempotency-Key on POST /api/v1/orders (201 first / 200 replay); Flyway V6
- Client sends random idempotency key on checkout
- Admin create product UI + `CreateProductRequestDto` / `QMarketApiClient.createProduct`
- Compose UI tests: Profile, Orders, Addresses, Admin (JVM `runComposeUiTest` v2)
- Login rate limiting: sliding window per email (`qmarket.auth.login-max-attempts` / `login-window-seconds`), HTTP 429


### Added
- Compose UI tests: `runComposeUiTest` for Login, Register, Catalog, Cart (JVM via `compose-ui-test`)
- testTags on login email/password fields
- Persistent JWT session: `SessionStore` + platform actuals (Android SharedPreferences, JVM `~/.qmarket/session.properties`, iOS NSUserDefaults, JS localStorage)
- `MutableTokenProvider` loads/saves via store; `App` restores session on launch
- Android: `initAndroidSessionStore` from MainActivity
- Session unit tests (`InMemorySessionStore`, reload from store)

### Changed
- Shared Compose UI split into `ui/` screens (`LoginScreen`, `CatalogScreen`, `CartScreen`, …)
- `App.kt` is composition root only (session, API, navigation)
- Catalog query mapping and checkout enable rules live in `CatalogFilterParams` / `CheckoutSelection` (used by UI and tests)
- Profile phone input uses the same `ClientInputValidation.filterPhoneInput` as addresses

### Added (earlier)
- Shared client: Ktor Bearer auth with automatic refresh on 401 (`/api/v1/auth/refresh`)
- `MutableTokenProvider.applyAuth` stores access + refresh; login/register persist both
- `QMarketApiClient.refresh` + `MutableTokenProviderTest`

### Docs
- ROADMAP v0.1 client checklist matches code (no longer “skeleton”)
- README Flyway V1–V5; Known limitations no longer claim missing KMP client

### Fixed
- Stock overselling: atomic `UPDATE … WHERE stock >= qty` (no JPA `@Version`)
- Drop orphan `products.version` column (Flyway V5); was unused after stock strategy change
- Address entity: `default` → `isDefault` (SQL/JPQL reserved word broke context startup)
- Seed gated by `qmarket.seed.enabled` (off under `prod` profile)
- CORS origins from config (no wildcard `*`)

### Added (earlier in WIP)
- Address book: Set default in shared UI
- `StockConcurrencyTest` — parallel decreaseStock never oversells
- `server/Dockerfile` multi-stage bootJar image
- `application-prod.yml` seed/CORS defaults
- Shared Compose UI: login, register, profile, addresses, catalog, cart, checkout, orders
- Ktor `QMarketApiClient` + MockEngine tests
- Shared KMP API DTOs in `:core`
- Order/Cart aggregates; `server:catalog-api` + ArchUnit
- Checkout `addressId`; addresses CRUD (Flyway V3)
- Change password; catalog filters; profile API; mock pay
- Flyway V1–V5; `spring-boot-starter-flyway`; ktlint; JaCoCo

### Known limitations
- Real PSP payments (mock only)
- Admin product CRUD UI (create/update/delete); categories UI still open
- Refresh tokens are DB-backed with rotation, family reuse detection, and logout revoke
- Compose UI tests cover key screens on JVM; Android instrumented / iOS UI tests not wired yet
- Session storage: Android EncryptedSharedPreferences + iOS Keychain; JVM file; wasmJs in-memory
- wasmJs session remains in-memory
