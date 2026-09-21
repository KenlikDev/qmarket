# Changelog

## Unreleased

### Security and correctness

- Database-backed refresh-token rotation with JTI tracking, family reuse detection, logout revocation, and session invalidation after password changes.
- Checkout idempotency with request fingerprints; reusing a key with a different request returns HTTP 409.
- Cart and checkout aggregate mutations use database row locking where concurrent changes can affect correctness.
- Address default-state mutations are serialized per user with a pessimistic user-row lock.
- Orders and products use optimistic locking; stock changes remain atomic database updates.
- Concurrent state and uniqueness conflicts are exposed as stable HTTP 409 responses.
- Public catalog endpoints do not expose inactive products or internal cost price.
- Login failures are rate-limited with bounded in-process storage using independent account and client-source limits.
- JWT configuration rejects missing or known placeholder secrets.
- Production disables anonymous Swagger/OpenAPI access, limits actuator exposure, and requires database/JWT credentials.
- Production error responses and database integrity logging use safe defaults.
- Android session storage uses encrypted preferences; iOS uses Keychain; JVM/JS/Wasm use target-appropriate stores.
- Password fields are masked in the shared Compose UI, and transient session-restore failures no longer destroy a valid local session.

### Payments

- `PaymentGateway` isolates the payment domain from provider implementations; mock remains the local default.
- Stripe PaymentIntent creation uses an idempotency key derived from the order id.
- Stripe webhook processing uses persistent PostgreSQL idempotency state, signature verification, amount reconciliation, currency validation, and retryable FAILED state.
- Stripe provider failures are represented as HTTP 502 responses rather than client input errors.
- Payment-session creation does not hold an open database transaction while waiting for Stripe.

### Client

- Shared KMP API DTOs and Ktor client cover authentication, catalog, cart, orders, profile, addresses, notifications, admin flows, and Stripe payment sessions.
- Session state is separated from screen composition through `QMarketAppModel` and dedicated account/admin extensions.
- Checkout reuses a pending idempotency key across retries and prevents duplicate local submissions.
- Client-side role handling uses an explicit administrator allow-list.

### Architecture and build

- Server is organized as a modular monolith with `common`, `identity`, `catalog-api`, `catalog`, `cart`, and `order` modules.
- `catalog-api` remains a pure module contract; ArchUnit tests enforce module boundaries.
- Flyway is the schema source of truth and application Hibernate mode is `validate`.
- `ktlint`, JaCoCo, Testcontainers integration tests, ArchUnit, and Gitleaks are part of the CI quality gates.
- Obsolete generated/template `bin/` sources were removed from the repository.
