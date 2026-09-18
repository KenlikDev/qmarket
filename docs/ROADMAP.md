# QMarket — план и версии

Источник: согласованный план разработки. Релиз версии — только после закрытия scope, зелёных тестов и подтверждения.

## Версии

| Версия | Фокус | Статус |
|--------|--------|--------|
| **v0.1 Foundation** | Backend + shared client shopper flow (auth→checkout→orders) | **Ready for release confirmation** |
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
- [x] Orders: create from cart, list, cancel, admin status, pay via PaymentGateway (mock default)
- [x] Profile + shipping addresses
- [x] Unit + controller slice + integration tests (incl. stock concurrency)
- [x] ktlint, JaCoCo, Docker Compose, Swagger JWT Authorize
- [x] Flyway SoT V1–V8, app `ddl-auto=validate`
- [x] `ProductCatalog` port + ArchUnit boundaries
- [x] Atomic stock `UPDATE … WHERE stock >= qty`

### Shared client (Compose + Ktor)

- [x] KMP DTOs in `:core` (kotlinx.serialization)
- [x] `QMarketApiClient` (auth, catalog, cart, orders, profile, addresses)
- [x] Login / register / profile / change password
- [x] Addresses (list/add/delete/set default) + checkout `addressId`
- [x] Catalog search/sort/featured
- [x] Product detail qty selector on add-to-cart
- [x] Catalog category filter (shopper chips → `categoryId`)
- [x] Product detail screen (GET product by id)
- [x] Cart quantity +/−, checkout, orders list, mock pay/cancel
- [x] Client checkout: stable Idempotency-Key + double-submit lock
- [x] Order detail screen (line items, pay/cancel/refresh)
- [x] JWT Bearer + refresh on 401
- [x] UI split: `ui/` screens + `App.kt` + `QMarketAppModel` (state/loaders outside composition root)
- [x] Persistent session store (Android prefs / JVM file / iOS defaults / JS localStorage)
- [x] Compose UI tests (`runComposeUiTest` on Login/Register/Catalog/Cart/Profile/Orders/Addresses; JVM)
- [x] Login rate limit (sliding window per email; 429 TOO_MANY_REQUESTS)

### Hardening (from architecture review)

- [x] Admin cancel restocks stock; cancel before restock
- [x] Strict order status state machine
- [x] Seed default off; prod JWT secret required
- [x] Checkout idempotency key (+ concurrent UNIQUE race → re-read winner)
- [x] Idempotency request fingerprint (Flyway V8; same key + different body → 409)
- [x] Refresh atomic consume (`revokeIfActive`)
- [x] JWT secret required (no placeholder default)
- [x] Product soft-delete (active=false)
- [x] Password change revokes refresh sessions
- [x] Concurrent refresh: no false family revoke
- [x] Concurrent checkout same Idempotency-Key integration test (`Idempotency-Key` header on POST /orders)
- [x] Refresh token rotation / revocation (V7, logout, reuse detection)

### v0.2 started

- [x] Admin: create / update / delete product UI + category assign (shared client, ROLE_ADMIN)
- [x] Admin: categories UI (list/create/edit/delete in Admin screen)
- [x] Admin: orders list + status transitions UI
- [x] Google OAuth (ID token -> JWT; POST /api/v1/auth/oauth/google, feature-flagged)

### v1.0 started

- [x] Admin users API: `GET /api/v1/admin/users` (+ `/{id}`, search `q`)
- [x] Admin users integration tests (Testcontainers)
- [x] Admin users UI in shared client (list + search q)
- [x] Admin users client tests (ApiClient + AdminScreen Compose)
- [x] Real PSP adapter skeleton: Stripe PaymentIntents (`provider=stripe`, mock remains default)
- [x] Stripe webhook (`POST /api/v1/payments/stripe/webhook`) + signature verify + markPaidFromProvider
- [x] Persistent webhook idempotency (V11 stripe_webhook_events) + amount reconciliation
- [x] Correlation id filter (MDC) + payment webhook metrics
- [x] CI: gitleaks secret scan, Testcontainers IT (no host Postgres), JaCoCo reports + soft coverage gates (order/identity 40%)
- [x] Dependabot (gradle + github-actions)

- [x] In-app notifications (order placed/paid/cancelled → `user_notifications`, API + client UI)
- [x] Admin status change notifies customer; TopBar Alerts badge (unread count)
- [x] P0 review hardening: no public costPrice, no seed password in OpenAPI/logs, order/product @Version, bulk markAllRead
- [x] P1: cart get-or-create race, rate-limit bounds+IP, actuator lockdown, cancel/pay concurrency tests
- [x] Android secure session storage (EncryptedSharedPreferences) + cleartext only in debug
- [x] iOS Keychain session storage (migrate from NSUserDefaults)
- [x] Prod: Swagger/OpenAPI off (`api-docs-public=false`, springdoc disabled)
- [x] Prod: actuator exposure limited to health/info
- [x] JwtProperties rejects known placeholder/dev secrets

## v1.0 MVP (из плана)

Покупатель: регистрация, каталог, корзина, заказ, оплата (мок + 1 провайдер), подтверждение.  
Админ: товары, заказы, пользователи.  
Платформы: Android + Web (+ Desktop); iOS параллельно.

Открыто для v1.0: distributed traces (optional); client Payment Element UI (Stripe.js / mobile SDK binding).
Сделано частично: coverage % gates (JaCoCo line ≥40% on `:server:order` + `:server:identity`; raise over time).
Сделано: payment-session API (`POST /api/v1/orders/{id}/payment-session` → client_secret) for Payment Element.
Сделано: correlation id (X-Correlation-Id / MDC), payment webhook Micrometer counters (`qmarket.payment.webhook.*`, `qmarket.payment.order.paid_from_provider`).
Google OAuth backend ready (enable via config).

Уже закрыто из прежнего списка: login rate limit, admin catalog/orders UI, refresh revoke/rotation, in-app notifications.

## Архитектура

Монолит → модульный монолит (сейчас) → микросервисы по нагрузке (v2.0+).

Контракты модулей — в `*.api` (как `catalog-api`), не в `common`.

Клиент: экраны в `app/shared/.../ui/`, состояние/API-сценарии в `QMarketAppModel`, сеть в `network/`, валидация в `validation/`.
См. также `docs/CLIENT_ARCHITECTURE.md`.

## Правило релиза

Версия не считается выпущенной, пока не закрыт согласованный scope, не зелёные тесты, не обновлена документация и нет явного подтверждения.
