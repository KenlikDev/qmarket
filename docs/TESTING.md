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
| API client | `app/shared` commonTest + MockEngine | DTO parsing, error mapping |
| Client validation | `ClientInputValidationTest` | Form rules match server intent |

## API correctness

- **Source of truth**: server integration tests (`AuthIntegrationTest`, `OrderIntegrationTest`, …).
- **Client contract**: `QMarketApiClientTest` with MockEngine (no network).
- Optional later: OpenAPI contract test, or Newman collection against `bootRun`.

## UI / multi-device

I (assistant) cannot drive your emulator. Automate instead:

1. **Now**: pure logic tests (`ClientInputValidation`) + API client tests.
2. **Next**: Compose UI tests (`testTag` + `compose-ui-test`) on **JVM desktop** and/or **Android**.
3. **Later**: screenshot tests (Roborazzi), Maestro/Appium for E2E on devices.

Compose Multiplatform UI tests need `testTag` on controls and a small test harness; add when flows stabilize.

## Manual smoke (when UI changes)

1. Login admin → catalog → add to cart → checkout → pay → orders.
2. Register new user (password ≥ 8).
3. Profile: reject digit-only name; phone accepts only digits/`+`.
