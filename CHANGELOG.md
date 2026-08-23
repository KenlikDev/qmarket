# Changelog

## Unreleased

### Added
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
- No login rate limiting
- Compose UI tests not yet wired (testTags exist)
- Session tokens are in-memory (lost on process restart)
