# QMarket — plan and versions

Source of truth for the agreed delivery plan. A version is released only after scope is closed, tests are green, and explicit confirmation.

## Versions

| Version | Focus | Status |
|--------|--------|--------|
| **v0.1 Foundation** | Backend + shared client shopper flow (auth→checkout→orders) | **Implemented; release confirmation pending** |
| **v0.2 Catalog & Auth polish** | OAuth optional, admin catalog UX | **WIP** (create product UI + rate limit done) |
| **v0.3** | (merged into v0.1 client) reserved / skip | done via shared UI |
| **v1.0 MVP** | Payments (1 provider), notifications, admin UI, search | pending |
| **v1.1 Growth** | Recommendations, reviews, promos, delivery integrations | pending |
| **v1.2 Scale** | Observability, cache, performance | pending |
| **v2.0** | Microservices evolution by need | pending |

## v0.1 Foundation — checklist (as built)

### Backend

- [x] Modular monolith Spring Boot 4.1 (`common`, `identity`, `catalog-api`, `catalog`, `cart`, `order`)
- [x] JWT auth register/login/refresh
- [x] Catalog CRUD + search + seed
- [x] Cart API
- [x] Orders: create from cart, list, cancel, admin status, pay via PaymentGateway (mock default)
- [x] Profile + shipping addresses
- [x] Unit + controller slice + integration tests (incl. stock concurrency)
- [x] ktlint, JaCoCo, Docker Compose, Swagger JWT Authorize
- [x] Flyway SoT V1–V11, app `ddl-auto=validate`
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
- [x] Persistent session store (Android EncryptedSharedPreferences / JVM file / iOS Keychain / JS localStorage; Wasm in-memory)
- [x] Compose UI tests (`runComposeUiTest` on Login/Register/Catalog/Cart/Profile/Orders/Addresses; JVM)
- [x] Login rate limit (sliding window per email + client key; 429 TOO_MANY_REQUESTS)

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

## v1.0 MVP (from plan)

Shopper: register, catalog, cart, order, payment (mock + 1 provider), confirmation.
Admin: products, orders, users.
Platforms: Android + Web (+ Desktop); iOS in parallel.

Still open for v1.0: distributed traces (optional); client Payment Element UI (Stripe.js / mobile SDK binding).
Partially done: coverage gates (JaCoCo line ≥40% on `:server:order` + `:server:identity`; raise over time).
Done: payment-session API (`POST /api/v1/orders/{id}/payment-session` → client_secret) for Payment Element.
Done: correlation id (X-Correlation-Id / MDC), payment webhook Micrometer counters (`qmarket.payment.webhook.*`, `qmarket.payment.order.paid_from_provider`).
Google OAuth backend ready (enable via config).

Already closed from earlier scope: login rate limit, admin catalog/orders UI, refresh revoke/rotation, in-app notifications.

## Architecture

Monolith → modular monolith (current) → microservices when load requires it (v2.0+).

Module contracts live in `*.api` modules (e.g. `catalog-api`), not in `common`.

Client: screens under `app/shared/.../ui/`, state/API flows in `QMarketAppModel`, networking in `network/`, validation in `validation/`.
See also `docs/CLIENT_ARCHITECTURE.md`.

## Release rule

A version is not released until the agreed scope is closed, tests are green, documentation is updated, and there is explicit confirmation.
