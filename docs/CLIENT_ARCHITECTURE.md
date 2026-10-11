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
- Browser JS: one canonical `localStorage` value with migration from the previous three keys. This prevents partial multi-key session updates but **does not protect tokens from same-origin JavaScript/XSS**; see the separate browser-auth hardening task.
- Wasm: in-memory session only; full page reload discards it.

Logout first commits a cleared-session tombstone, then attempts to remove legacy entries. The canonical tombstone takes precedence over any stale legacy keys, preventing a later launch from resurrecting a session if legacy cleanup fails. Persistence errors are surfaced to the caller. Session-store regression tests cover migration, failed writes, partial cleanup, process/store recreation, and desktop file permissions.

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
