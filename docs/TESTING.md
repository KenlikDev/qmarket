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
```

Secret scanning is also required by the protected-branch ruleset.

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

## API correctness

- **Source of truth**: server integration tests (`AuthIntegrationTest`, `OrderIntegrationTest`, …).
- **Client contract**: `QMarketApiClientTest` with MockEngine (no network).
- Optional later: OpenAPI contract test, or Newman collection against `bootRun`.

## UI / multi-device

1. **Current**: JVM logic/API/Compose tests (`./gradlew :app:shared:jvmTest`).
2. **Current CI platform verification**: iOS simulator native compilation.
3. **Next**: Android instrumented Compose tests and broader device runtime coverage.
4. **Later**: screenshot tests and full E2E on devices.

## Manual smoke (when UI changes)

1. Login admin → catalog → add to cart → checkout → pay → orders.
2. Register new user (password ≥ 8).
3. Profile: reject digit-only name; phone accepts only digits/`+`.
4. Addresses: add, set default; checkout with saved address.
