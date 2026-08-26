# Testing strategy (QMarket)

## What runs in CI / local

```bash
./gradlew test ktlintCheck --parallel
```

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
| Compose UI | `ui/*ScreenTest` + `runComposeUiTest` (JVM) | testTags, enabled state, click callbacks |

## API correctness

- **Source of truth**: server integration tests (`AuthIntegrationTest`, `OrderIntegrationTest`, …).
- **Client contract**: `QMarketApiClientTest` with MockEngine (no network).
- Optional later: OpenAPI contract test, or Newman collection against `bootRun`.

## UI / multi-device

1. **Now**: pure logic tests + API client tests + **Compose UI tests on JVM** (`./gradlew :app:shared:jvmTest`).
2. **Next**: Android instrumented Compose tests / more screen coverage (Profile, Orders, Addresses).
3. **Later**: screenshot tests (Roborazzi), Maestro/Appium for E2E on devices.

## Manual smoke (when UI changes)

1. Login admin → catalog → add to cart → checkout → pay → orders.
2. Register new user (password ≥ 8).
3. Profile: reject digit-only name; phone accepts only digits/`+`.
4. Addresses: add, set default; checkout with saved address.
