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
bash scripts/check-web-stripe-return-security.sh
./gradlew :app:shared:iosSimulatorArm64Test --no-daemon
xvfb-run -a ./gradlew :app:shared:jvmTest --no-daemon
./gradlew :app:androidApp:assembleDebug :app:androidApp:testDebugUnitTest :app:desktopApp:compileKotlin :app:shared:compileKotlinJs :app:shared:compileKotlinWasmJs :app:webApp:compileKotlinJs :app:webApp:compileKotlinWasmJs --parallel --no-daemon
```

The `client-platform-check` job runs `:app:shared:jvmTest` (the common KMP and JVM Compose/API/session-store tests) under Xvfb, then compiles Android Debug, Desktop/JVM, shared JS/Wasm, and web-app JS/Wasm targets. `ios-native-check` compiles the Kotlin/Native framework and runs `iosSimulatorArm64Test`, including state/recovery tests for the native Stripe payment host; `ios-app-check` builds the full iOS app and Stripe PaymentSheet Swift Package. The protected-branch-required `test` status is a final aggregate gate: it passes only when `server-tests`, `client-platform-check`, `ios-native-check`, `ios-app-check`, and `secret-scan` all succeed. These automated checks are not a substitute for full device/browser payment sandbox tests.

| Layer | Where | What it proves |
|-------|--------|----------------|
| Unit (domain/services) | `server/*/src/test` | Business rules, stock atomicity helpers |
| Controller slice | `*ControllerTest` | HTTP mapping + security annotations |
| Integration | `server/src/test` + Testcontainers | Real Postgres + full HTTP stack |
| ArchUnit | `ModuleArchitectureTest` | Module boundaries |
| API client | `app/shared` commonTest + MockEngine | DTO parsing, error mapping, refresh |
| Browser authentication | `AuthIntegrationTest` + controller/client tests | HttpOnly refresh cookie, rotation, logout clearing, allowed-Origin rejection, and refresh after page reload |
| Client validation | `ClientInputValidationTest` | Form rules match server intent |
| Catalog/checkout rules | `CatalogFilterParamsTest`, `CheckoutSelectionTest` | Same objects the UI calls |
| Session store | `MutableTokenProviderTest`, `InMemorySessionStoreTest` | Persist + reload tokens |
| Compose UI | `Login/Register/Catalog/Cart/Profile/Orders/Addresses*ScreenTest` | testTags, enabled state, pay/cancel, validation |
| Login rate limit | `LoginRateLimiterTest` | lock after N failures; clear on success |
| Admin UI | `AdminScreenTest` | create/update form; delete; slugify |
| Admin API client | `createProductParsesResponse` | POST /products envelope |
| Payment lifecycle | `OrderPaymentServiceTest`, `OrderStatusConcurrencyTest`, scheduler tests | expiry, cancellation recovery, webhook race, terminal operation-state invariants |
| iOS Stripe payment host | `IosStripePaymentHostTest` (`iosSimulatorArm64Test`) | single active payment, stale callback rejection, one-shot result handling, pending-order recovery after process restart without persisting the client secret or pretending the SDK completed |
| Durable data retention | `DataRetentionCleanupSchedulerTest`, `DataRetentionIntegrationTest` | configurable retention windows, bounded batch cleanup, and non-deletion of fresh rows |

## API correctness

- **Source of truth**: server integration tests (`AuthIntegrationTest`, `OrderIntegrationTest`, …).
- **Client contract**: `QMarketApiClientTest` with MockEngine (no network).
- Optional later: OpenAPI contract test, or Newman collection against `bootRun`.

## UI / multi-device

1. **Current**: JVM logic/API/Compose tests (`./gradlew :app:shared:jvmTest`).
2. **Current CI platform verification**: Android Debug, Desktop/JVM, shared JS/Wasm, web-app JS/Wasm, iOS simulator Kotlin/Native compilation, and Xcode iOS app build with Stripe PaymentSheet via Swift Package Manager.
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
8. JS browser Stripe sandbox: use a test PaymentIntent, exercise card success and a redirect/3DS payment method where available, return to QMarket, and verify the order is reloaded from the authenticated API rather than marked PAID from a browser callback. Verify the URL no longer contains `payment_intent_client_secret` after return.
9. JS browser Stripe sandbox: simulate a decline/network failure, cancel the overlay, retry, and verify that unavailable Stripe.js or an unsupported Wasm target fails closed without marking the order paid.
10. Android session-store migration: upgrade an install with existing EncryptedSharedPreferences, verify the session survives restart, and verify the old encrypted/plaintext preferences are cleared after migration.
11. Android session-store migration: upgrade an install with only legacy plaintext keys; verify values are moved into the Keystore-backed canonical record and no plaintext keys remain.
12. Android session-store logout: clear the session, force-stop/restart, and verify old split keys do not restore the session.


## Browser refresh-cookie smoke

1. Verify login/register places the refresh JWT only in the `HttpOnly; Secure; SameSite` cookie; the JSON response's `refreshToken` field must be empty.
2. Reload the page. The JS store must contain no persistent tokens, and the client must refresh using the cookie before loading the profile.
3. Confirm logout clears the cookie and a subsequent reload does not restore the previous session.
4. Verify an untrusted Origin is rejected for cookie refresh/logout. Configure exact `QMARKET_WEB_AUTH_ALLOWED_ORIGINS` and matching `CORS_ORIGINS` before browser runtime testing.
5. Upgrading from the older JS client removes the token-bearing legacy `localStorage` keys; the user must sign in again once.


## Browser Stripe return URL protection

- CI runs `bash scripts/check-web-stripe-return-security.sh` to ensure the early referrer policy and inline client-secret redaction remain before stylesheet/app asset requests, and that the Kotlin adapter consumes the sanitized marker.
- This guard only prevents the secret being retained in the page URL/history after the HTML is parsed and being forwarded as a Referer. It cannot prevent the first document request from being recorded with its query string; production CDN/static-host access logs must redact query strings or the deployment must use a host-specific safe return route.
- Manually verify that after a Stripe redirect, `payment_intent_client_secret` is absent before the web app requests its JS/CSS assets and that the order is reloaded from the authenticated API.

## iOS Stripe sandbox smoke

1. Run the iOS app on a simulator/device with a test API environment and Stripe test keys.
2. Open a pending order and tap **Pay securely with Stripe**; verify PaymentSheet opens once and refuses a second session while it is active.
3. Complete a test payment including a 3DS/redirect flow where available; verify the registered return URL is handled and the shared model reloads the order from the API.
4. Cancel and run a declined/test failure; verify the order remains PENDING and the UI offers retry/cancel without claiming PAID before server webhook reconciliation.
5. Confirm the client secret is only passed in memory to PaymentSheet, never persisted/logged, and that the app's registered return URL has the scheme `com.kenlikdev.qmarket.stripe`.
