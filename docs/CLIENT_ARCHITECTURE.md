# Client architecture (shared)

## Layers

| Layer | Location | Role |
|-------|----------|------|
| Screens | `app/shared/.../ui/*Screen.kt` | Stateless UI + callbacks |
| App shell | `App.kt` | `MaterialTheme` + `when (AppScreen)` wiring |
| App model | `QMarketAppModel.kt` | Mutable UI state, `runApi`, loaders, login/checkout/admin |
| Network | `network/QMarketApiClient.kt`, `SessionStore`, platform HTTP | REST + tokens |
| DTOs | `:core` `api/*` | kotlinx.serialization shared with server contracts |

## Session

- `MutableTokenProvider` + platform `SessionStore`; access token, refresh token and email are persisted as one versioned session record where the platform supports it.
- Android: one encrypted SharedPreferences value, committed synchronously. The existing plaintext `qmarket_session` preferences and old encrypted split keys are migrated forward.
- iOS: one Keychain item (CFDictionary / CFData) for the complete session; old per-field Keychain accounts are migrated forward.
- JVM/Desktop: one private `session.properties` file replaced through a temporary file and atomic move where supported. On POSIX, the containing directory is owner-only and traversable (`0700`); the session file is owner read/write only (`0600`).
- Browser JS: the short-lived access token stays in memory; the rotating refresh token is an `HttpOnly; Secure; SameSite=Strict` cookie scoped to `/api/v1/auth` by default. Browser refresh/logout requires the `X-QMarket-Web-Auth: 1` header plus an allowed `Origin`; the cookie is not used to authenticate ordinary API calls. The JS session store is memory-only and removes legacy token-bearing `localStorage` keys on startup, so users must sign in again once after upgrading. This reduces refresh-token exfiltration by XSS but does not prevent malicious same-origin script from performing actions while the page is active.
- Wasm: in-memory session only; full page reload discards it.

Logout first commits a cleared-session tombstone, then attempts to remove legacy entries. The canonical tombstone takes precedence over any stale legacy keys, preventing a later launch from resurrecting a session if legacy cleanup fails. If the tombstone write itself fails, the app preserves the in-memory/UI session and does not request server refresh-token revocation; if the tombstone commits but legacy cleanup fails, the app completes local logout and surfaces a cleanup warning. Remote refresh-token revocation is attempted only after durable local logout. Session-store regression tests cover migration, failed writes, partial cleanup, process/store recreation, and desktop file permissions.

## Adding a feature

1. DTO in `:core` if new API shape
2. Method on `QMarketApiClient`
3. State + action on `QMarketAppModel`
4. Screen in `ui/` + branch in `App.kt` `when`
5. JVM Compose test and/or client MockEngine test

## Verification

```bash
./gradlew :app:shared:compileKotlinIosSimulatorArm64 ktlintCheck --parallel
./gradlew :app:shared:jvmTest ktlintCheck --parallel
./gradlew test ktlintCheck --parallel
```


## Payments

- Server returns a Stripe PaymentIntent client secret and publishable key only to the authenticated owner of an eligible order.
- Android uses the native Stripe PaymentSheet. The client secret is held only in in-memory model state and is not persisted or logged. The Activity-scoped host ViewModel retains only the pending order id and a one-shot result event in SavedStateHandle, so callback routing can survive Activity/process recreation without persisting payment credentials.
- The PaymentSheet callback is treated as a client-side UX result only. The app reloads the order from the API; only the server's verified Stripe webhook can make an order `PAID`.
- The Android UI keeps a completed PaymentSheet distinct from a server-confirmed paid order. If the webhook has not updated the order yet, the order stays `PENDING` and the user can refresh.
- Stripe confirmation is implemented for Android through PaymentSheet and the JS browser through Stripe.js Payment Element. Browser redirects for 3DS/payment-method authentication return with an order identifier; Stripe's redirect query fields are removed from browser history and the order is reloaded from the authenticated API. iOS, Desktop/JVM and Wasm fail closed and do not create a PaymentIntent from an unsupported client. This is a partial milestone for issue #182; browser sandbox and Android device smoke tests remain release checks.

## Android session storage

- Active session values are stored as a single canonical record encrypted with AES-GCM using a non-exportable key held by Android Keystore.
- The encrypted payload authenticates its preference-key name as AES-GCM additional authenticated data. A corrupt or unreadable canonical record fails closed; it is never replaced with an older token tuple.
- The old `EncryptedSharedPreferences` file is read-only migration input. After the new canonical record is committed, both legacy encrypted and plaintext preference files are cleared.
- The encrypted legacy file wins over plaintext legacy values. If it exists but cannot be decrypted, the app commits a cleared tombstone before deleting old files; it does not fall back to plaintext tokens.
- Logout's canonical cleared tombstone remains authoritative across restart and interrupted cleanup.
- `androidx.security:security-crypto` remains temporarily as a migration-only dependency so already-installed app versions can be upgraded without losing the ability to decrypt their existing sessions. Current writes do not use that deprecated API.

Android device migration/Keystore smoke tests are still required before release; host CI covers the migration precedence logic, not hardware-backed Keystore behavior.


### Browser refresh-cookie configuration

- Configure `QMARKET_WEB_AUTH_ALLOWED_ORIGINS` with exact trusted web origins in production (scheme + host + port, no path). Browser cookie operations reject absent or untrusted `Origin` headers even if the general CORS list is broader.
- Keep `CORS_ORIGINS` aligned with those origins so credentialed fetch requests can pass preflight.
- Defaults use Secure + HttpOnly + SameSite=Strict. If the web app and API are genuinely cross-site, explicitly configure SameSite=None and retain Secure; verify deployment topology and CSRF behavior before enabling it.
- Native clients continue sending refresh tokens in JSON and receive them in JSON. Only requests carrying the valid browser marker switch to cookie-based refresh.
