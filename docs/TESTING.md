# Testing strategy (QMarket)

## What runs in CI / local

Local baseline:

```bash
./gradlew test ktlintCheck --parallel
```

CI additionally runs:

```bash
./gradlew test ktlintCheck jacocoTestReport --parallel --no-daemon
./gradlew :server:order:jacocoTestCoverageVerification :server:identity:jacocoTestCoverageVerification --parallel --no-daemon
./gradlew :app:shared:compileKotlinIosSimulatorArm64 --no-daemon
./gradlew :app:androidApp:assembleDebug :app:androidApp:testDebugUnitTest :app:desktopApp:compileKotlin :app:shared:compileKotlinJs :app:shared:compileKotlinWasmJs :app:webApp:compileKotlinJs :app:webApp:compileKotlinWasmJs --parallel --no-daemon
```

The platform-build job compiles Android Debug, Desktop/JVM, shared JS/Wasm, and web-app JS/Wasm targets. These are compile checks, not full device/browser runtime or end-to-end checks. Secret scanning is also required by the protected-branch ruleset.

| Layer | Where | What it proves |
|-------|--------|----------------|
| Unit (domain/services) | `server/*/src/test` | Business rules, stock atomicity helpers |
| Controller slice | `*ControllerTest` | HTTP mapping + security annotations |
| Integration | `server/src/test` + Testcontainers | Real Postgres + full HTTP stack |
| ArchUnit | `ModuleArchitectureTest` | Module boundaries |
| API client | `app/shared` commonTest + MockEngine | DTO parsing, error mapping, refresh |
| Client validation | `ClientInputValidationTest` | Form rules match server intent |
| Catalog/checkout rules | `CatalogFilterParamsTest`, `CheckoutSelectionTest` | Same objects the UI calls |
| Session store | `MutableTokenProviderTest`, `InMemorySessionStoreTest` | Persist + reload tokens |
| Compose UI | `Login/Register/Catalog/Cart/Profile/Orders/Addresses*ScreenTest` | testTags, enabled state, pay/cancel, validation |
| Login rate limit | `LoginRateLimiterTest` | lock after N failures; clear on success |
| Admin UI | `AdminScreenTest` | create/update form; delete; slugify |
| Admin API client | `createProductParsesResponse` | POST /products envelope |
| Payment lifecycle | `OrderPaymentServiceTest`, `OrderStatusConcurrencyTest`, scheduler tests | expiry, cancellation recovery, webhook race, terminal operation-state invariants |
| Durable data retention | `DataRetentionCleanupSchedulerTest`, `DataRetentionIntegrationTest` | configurable retention windows, bounded batch cleanup, and non-deletion of fresh rows |

## API correctness

- **Source of truth**: server integration tests (`AuthIntegrationTest`, `OrderIntegrationTest`, …).
- **Client contract**: `QMarketApiClientTest` with MockEngine (no network).
- Optional later: OpenAPI contract test, or Newman collection against `bootRun`.

## UI / multi-device

1. **Current**: JVM logic/API/Compose tests (`./gradlew :app:shared:jvmTest`).
2. **Current CI platform verification**: Android Debug, Desktop/JVM, shared JS/Wasm, web-app JS/Wasm, and iOS simulator compilation.
3. **Next**: Android instrumented Compose tests and broader device/browser runtime coverage.
4. **Later**: screenshot tests and full E2E on devices.

## Manual smoke (when UI changes)

1. Login admin → catalog → add to cart → checkout → pay → orders.
2. Register new user (password ≥ 8).
3. Profile: reject digit-only name; phone accepts only digits/`+`.
4. Addresses: add, set default; checkout with saved address.
5. Android Stripe sandbox: create a session, complete a test payment requiring 3DS where available, and verify the UI does not show PAID until the server webhook has updated the order.
6. Android Stripe sandbox: rotate/recreate the host Activity while PaymentSheet is open, then verify its result still refreshes the correct order without persisting/logging the client secret.
7. Android Stripe sandbox: cancel the sheet and simulate a declined payment; verify the order remains PENDING, can be retried, and can still be cancelled.
8. Android session-store migration: upgrade an install with existing EncryptedSharedPreferences, verify the session survives restart, and verify the old encrypted/plaintext preferences are cleared after migration.
9. Android session-store migration: upgrade an install with only legacy plaintext keys; verify values are moved into the Keystore-backed canonical record and no plaintext keys remain.
10. Android session-store logout: clear the session, force-stop/restart, and verify old split keys do not restore the session.
