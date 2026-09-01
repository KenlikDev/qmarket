# QMarket

Онлайн-магазин на **Kotlin Multiplatform + Spring Boot 4.1** (модульный монолит).

План и версии: [docs/ROADMAP.md](docs/ROADMAP.md)

## Текущее состояние (WIP, не релиз v0.1)

| Область | Статус |
|--------|--------|
| Auth (JWT) | ✅ |
| Catalog | ✅ |
| Cart | ✅ |
| Orders (checkout from cart) | ✅ |
| Seed admin + demo catalog | ✅ |
| Unit / controller / integration tests | ✅ |
| ktlint, JaCoCo, Swagger Authorize | ✅ |
| Flyway SoT (`spring-boot-starter-flyway`) | ✅ V1–V5, app: `validate`, tests: Flyway |
| Payments (mock) | ✅ POST /api/v1/orders/{id}/pay |
| Profile (GET/PATCH /users/me) | ✅ |
| Shipping addresses | ✅ |
| catalog-api (ProductCatalog port) | ✅ |
| ArchUnit module boundaries | ✅ |
| Shared KMP DTOs (`core` api) | ✅ kotlinx.serialization |
| Ktor ApiClient (shared) | ✅ auth/catalog/cart/orders; Bearer + refresh on 401 |
| Clients UI | ✅ screens in `app/shared/.../ui/`; `App.kt` composition root |
| Session persistence | ✅ platform SessionStore (Android EncryptedSharedPreferences / iOS Keychain / JVM file / JS localStorage) |
| Compose UI tests (JVM) | ✅ Login / Register / Catalog / Cart |
| Stock concurrency | ✅ atomic UPDATE + IT |

## Quick start

```bash
# При смене схемы (один раз):
# docker compose down -v && docker compose up -d

docker compose up -d
./gradlew :server:bootRun
```

- Swagger: http://localhost:8080/swagger-ui.html (Authorize = JWT accessToken)
- Admin: `admin@qmarket.local` / `admin123`

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

Первый адрес или `default: true` становится адресом по умолчанию (один на пользователя).

### Profile API

```
GET    /api/v1/users/me
PATCH  /api/v1/users/me   { "firstName"?, "lastName"?, "phone"? }
POST   /api/v1/users/me/password  { "currentPassword", "newPassword" }  # 204
```

### Orders API

```
POST   /api/v1/orders                 # { shippingAddress? | addressId?, customerNote? }
GET    /api/v1/orders?page=0&size=20  # без sort (Swagger sort=string ломал JPA)
GET    /api/v1/orders/{id}
POST   /api/v1/orders/{id}/cancel
POST   /api/v1/orders/{id}/pay     # mock payment → PAID
GET    /api/v1/orders/admin/all
GET    /api/v1/orders/admin/{id}
PUT    /api/v1/orders/admin/{id}/status  { "status": "CONFIRMED" }
```

Статусы: `PENDING | CONFIRMED | PAID | SHIPPED | DELIVERED | CANCELLED`

### Client (shared Compose)

```bash
# backend must be running
./gradlew :server:bootRun
# desktop:
./gradlew :app:desktopApp:run
```

Default login: `admin@qmarket.local` / `admin123`  
Android emulator API host: `10.0.2.2:8080`

### Client flow

1. Login / Register (or anonymous catalog)
1b. Profile (edit name/phone, change password)
1c. Addresses (list/add/delete; use on checkout)
2. Add to cart (requires login)
3. Cart → checkout with shipping address
4. Optional mock pay on order confirmation
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
- Stock: atomic `UPDATE … WHERE stock >= qty` (DB row lock; no JPA `@Version`)

### Тесты и качество (всегда по всему проекту)

```bash
./gradlew test ktlintCheck --parallel
```

Точечно только для отладки: `./gradlew :server:order:test`
