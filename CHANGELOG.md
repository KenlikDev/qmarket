# Changelog

## Unreleased

### Added
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
- No admin UI yet
- Refresh tokens are not revocable (stateless JWT)
- Compose UI tests cover key screens on JVM; Android instrumented / iOS UI tests not wired yet
- Session storage is app-private but not hardware-backed (no EncryptedSharedPreferences / Keychain yet)
- wasmJs session remains in-memory
