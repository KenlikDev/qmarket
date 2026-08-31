# QMarket — план и версии

Источник: согласованный план разработки. Релиз версии — только после закрытия scope, зелёных тестов и подтверждения.

## Версии

| Версия | Фокус | Статус |
|--------|--------|--------|
| **v0.1 Foundation** | Backend + shared client shopper flow (auth→checkout→orders) | **WIP** (scope почти закрыт) |
| **v0.2 Catalog & Auth polish** | OAuth optional, admin catalog UX | **WIP** (create product UI + rate limit done) |
| **v0.3** | (merged into v0.1 client) reserved / skip | done via shared UI |
| **v1.0 MVP** | Payments (1 провайдер), notifications, admin UI, search | pending |
| **v1.1 Growth** | Recommendations, reviews, promos, delivery integrations | pending |
| **v1.2 Scale** | Observability, cache, performance | pending |
| **v2.0** | Microservices evolution by need | pending |

## v0.1 Foundation — чеклист (факт)

### Backend

- [x] Modular monolith Spring Boot 4.1 (`common`, `identity`, `catalog-api`, `catalog`, `cart`, `order`)
- [x] JWT auth register/login/refresh
- [x] Catalog CRUD + search + seed
- [x] Cart API
- [x] Orders: create from cart, list, cancel, admin status, mock pay
- [x] Profile + shipping addresses
- [x] Unit + controller slice + integration tests (incl. stock concurrency)
- [x] ktlint, JaCoCo, Docker Compose, Swagger JWT Authorize
- [x] Flyway SoT V1–V5, app `ddl-auto=validate`
- [x] `ProductCatalog` port + ArchUnit boundaries
- [x] Atomic stock `UPDATE … WHERE stock >= qty`

### Shared client (Compose + Ktor)

- [x] KMP DTOs in `:core` (kotlinx.serialization)
- [x] `QMarketApiClient` (auth, catalog, cart, orders, profile, addresses)
- [x] Login / register / profile / change password
- [x] Addresses (list/add/delete/set default) + checkout `addressId`
- [x] Catalog search/sort/featured
- [x] Cart quantity +/−, checkout, orders list, mock pay/cancel
- [x] JWT Bearer + refresh on 401
- [x] UI split: `ui/` screens + `App.kt` composition root
- [x] Persistent session store (Android prefs / JVM file / iOS defaults / JS localStorage)
- [x] Compose UI tests (`runComposeUiTest` on Login/Register/Catalog/Cart/Profile/Orders/Addresses; JVM)
- [x] Login rate limit (sliding window per email; 429 TOO_MANY_REQUESTS)

### Hardening (from architecture review)

- [x] Admin cancel restocks stock; cancel before restock
- [x] Strict order status state machine
- [x] Seed default off; prod JWT secret required
- [x] Checkout idempotency key (+ concurrent UNIQUE race → re-read winner)
- [x] Refresh atomic consume (`revokeIfActive`)
- [x] JWT secret required (no placeholder default)
- [x] Product soft-delete (active=false)
- [x] Password change revokes refresh sessions
- [x] Concurrent refresh: no false family revoke
- [x] Concurrent checkout same Idempotency-Key (`pg_advisory_xact_lock` + `OrderCheckoutConcurrencyTest`; sequential HTTP replay)
- [x] Refresh token rotation / revocation (V7, logout, reuse detection)

### v0.2 started

- [x] Admin: create / update / delete product UI (shared client, ROLE_ADMIN)
- [x] Admin: categories UI (list/create/delete in Admin screen)
- [x] Admin: orders list + status transitions UI
- [ ] OAuth (optional)

## v1.0 MVP (из плана)

Покупатель: регистрация, каталог, корзина, заказ, оплата (мок + 1 провайдер), подтверждение.  
Админ: товары, заказы, пользователи.  
Платформы: Android + Web (+ Desktop); iOS параллельно.

Открыто для v1.0: реальный PSP, rate limit login, admin UI, отзыв refresh-токенов.

## Архитектура

Монолит → модульный монолит (сейчас) → микросервисы по нагрузке (v2.0+).

Контракты модулей — в `*.api` (как `catalog-api`), не в `common`.

Клиент: экраны в `app/shared/.../ui/`, сеть в `network/`, валидация в `validation/`.

## Правило релиза

Версия не считается выпущенной, пока не закрыт согласованный scope, не зелёные тесты, не обновлена документация и нет явного подтверждения.
