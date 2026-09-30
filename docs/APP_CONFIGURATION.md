# Application configuration

## API base URL

The shared client validates the API endpoint before creating the Ktor client. Production and any non-local deployment must use HTTPS. Plain HTTP is accepted only for localhost, loopback, or the Android emulator host `10.0.2.2`.

Android debug defaults to:
`http://10.0.2.2:8080`

Android release reads the Gradle property `qmarketApiBaseUrl`:
`./gradlew :app:androidApp:assembleRelease -PqmarketApiBaseUrl=https://api.example.com`

iOS reads `QMARKET_API_BASE_URL` from the Xcode configuration. The repository default remains localhost for development; release CI/xcodebuild should override it with an HTTPS endpoint:
`xcodebuild ... QMARKET_API_BASE_URL=https://api.example.com`

JVM/Desktop reads `qmarket.api.base-url` system property first, then `QMARKET_API_BASE_URL` environment variable.

Browser builds use `window.__QMARKET_API_BASE_URL` when provided, otherwise the current page origin. This supports same-origin production hosting without embedding an environment-specific URL in the shared binary.

No API secret belongs in the client configuration; the base URL is not a credential.
