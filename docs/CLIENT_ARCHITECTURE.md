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

- `MutableTokenProvider` + platform `SessionStore`
- Android: EncryptedSharedPreferences
- iOS: Keychain (CFDictionary / CFData)
- JVM/JS: file / localStorage

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
