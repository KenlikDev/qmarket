# QMarket

Online shop built with **Kotlin Multiplatform + Spring Boot 4.1** (modular monolith).

Plan and versions: [docs/ROADMAP.md](docs/ROADMAP.md)

## Current status (WIP, not a v0.1 release)

| Area | Status |
|--------|--------|
| Auth (JWT) | ✅ |
| Catalog | ✅ |
| Cart | ✅ |
| Orders (checkout from cart) | ✅ |
| Seed admin + demo catalog | ✅ |
| Unit / controller / integration tests | ✅ |
| ktlint, JaCoCo, Swagger Authorize | ✅ |
| Flyway SoT (`spring-boot-starter-flyway`) | ✅ V1–V14, app: `validate`, tests: Flyway |
| Payments | ✅ mock locally; Stripe PaymentIntent + webhook path available |
| Profile (GET/PATCH /users/me) | ✅ |
| Shipping addresses | ✅ |
| catalog-api (ProductCatalog port) | ✅ |
| ArchUnit module boundaries | ✅ |
| Shared KMP DTOs (`core` api) | ✅ kotlinx.serialization |
| Ktor ApiClient (shared) | ✅ auth/catalog/cart/orders; Bearer + refresh on 401 |
| Clients UI | ✅ screens in `app/shared/.../ui/`; `App.kt` composition root |
| Session persistence | ✅ platform SessionStore (Android EncryptedSharedPreferences / iOS Keychain / JVM file / JS localStorage; Wasm in-memory) |
| Compose UI tests (JVM) | ✅ Login / Register / Catalog / Cart |
| Stock concurrency | ✅ atomic UPDATE + IT |

## Quick start

```bash
# After schema changes (once):
# docker compose down -v && docker compose up -d

docker compose up -d
./gradlew :server:bootRun --args="--spring.profiles.active=dev"
```

- Swagger (dev profile): http://localhost:8080/swagger-ui.html (Authorize = JWT accessToken)
- Local demo admin (when the `dev` profile is active): `admin@qmarket.local` / password from `QMARKET_SEED_ADMIN_PASSWORD` (defaults to `admin123` in the dev profile)

### Catalog filters

Shared UI: search field, Newest/Price/Name, Featured toggle, Apply.


```
GET /api/v1/products?q=&categoryId=&featuredOnly=&minPrice=&maxPrice=&sortBy=price|name|createdAt&sortDir=asc|desc&page=&size=
```

### Addresses API

```
GET    /api/v1/users/me/addresses
POST   /api/v1/users/me/addresses
GET    /api/v1/users/me/addresses/{id}
PUT    /api/v1/users/me/addresses/{id}
DELETE /api/v1/users/me/addresses/{id}
```

The first address or `default: true` becomes the default address (one per user).

### Profile API

```
GET    /api/v1/users/me
PATCH  /api/v1/users/me   { "firstName"?, "lastName"?, "phone"? }
POST   /api/v1/users/me/password  { "currentPassword", "newPassword" }  # 204
```

### Orders API

```
POST   /api/v1/orders                 # { shippingAddress? | addressId?, customerNote? }
GET    /api/v1/orders?page=0&size=20  # no sort param (Swagger sort=string broke JPA)
GET    /api/v1/orders/{id}
POST   /api/v1/orders/{id}/cancel
POST   /api/v1/orders/{id}/pay     # local mock gateway; production uses Stripe client payment flow
GET    /api/v1/orders/admin/all
GET    /api/v1/orders/admin/{id}
PUT    /api/v1/orders/admin/{id}/status  { "status": "CONFIRMED" }
```

Statuses: `PENDING | CONFIRMED | PAID | SHIPPED | DELIVERED | CANCELLED`

### Client (shared Compose)

```bash
# backend must be running
./gradlew :server:bootRun
# desktop:
./gradlew :app:desktopApp:run
```

Local demo login (dev profile): `admin@qmarket.local` / `admin123`  
Android emulator API host: `10.0.2.2:8080`

### Client flow

1. Login / Register (or anonymous catalog)
1b. Profile (edit name/phone, change password)
1c. Addresses (list/add/delete; use on checkout)
2. Add to cart (requires login)
3. Cart → checkout with shipping address
4. Complete payment using the configured provider (mock locally; Stripe PaymentIntent in production)
5. My orders — list, pay, cancel (PENDING)

### Production notes

- Profile `prod`: Swagger/OpenAPI **disabled**; `qmarket.security.api-docs-public=false`
- `JWT_SECRET` required (≥32 chars, not a known placeholder); generate: `openssl rand -base64 48`
- Actuator in prod exposes only `health,info` (no prometheus/metrics by default)
- Android release: no cleartext HTTP; tokens in EncryptedSharedPreferences
- Android debug: cleartext allowed for emulator (`10.0.2.2` / localhost) via network security debug-overrides

- Seed: `qmarket.seed.enabled=false` by default; profile `prod` keeps it off
- Local demo data: Spring profile `dev` (`server/.../application-dev.yml` sets seed on)
  ```bash
  ./gradlew :server:bootRun --args="--spring.profiles.active=dev"
  ```
  Alternative without profile: `QMARKET_SEED_ENABLED=true`
  Demo admin: `admin@qmarket.local` / `admin123`
- CORS: `CORS_ORIGINS` / `qmarket.security.cors.allowed-origin-patterns`
- Stock: atomic `UPDATE … WHERE stock >= qty` with database-side concurrency control
- Database: V12–V14 restore foreign-key integrity and persist stable Google OAuth identity; application Hibernate mode remains validation-only.

### Tests and quality (full project)

```bash
./gradlew test ktlintCheck --parallel
```

Single-module debug only: `./gradlew :server:order:test`
