# QMarket client API endpoint configuration

`App` no longer owns a production endpoint. Each application entry point supplies the endpoint for its target, and the shared validator rejects non-local plain HTTP.

## Android

Debug builds use `http://10.0.2.2:8080` for the Android emulator.

Release builds require a Gradle property and HTTPS:

```text
./gradlew :app:androidApp:assembleRelease -PqmarketApiBaseUrl=https://api.example.com
```

The API URL is compiled into a resource; do not commit a real production endpoint or secret.

## iOS

Debug uses the local endpoint from `app/iosApp/Configuration/Config.xcconfig`.

Release reads `QMARKET_API_BASE_URL` from the Xcode build setting and requires HTTPS at runtime. CI/release builds must override it, for example:

```text
xcodebuild ... -configuration Release QMARKET_API_BASE_URL=https://api.example.com
```

`QMARKET_BUILD_CONFIGURATION` is generated from Xcode's `CONFIGURATION` setting and is used to fail closed for Release builds.

## Desktop

Set `QMARKET_API_BASE_URL` or the `qmarket.api.base-url` JVM system property.

```text
QMARKET_API_BASE_URL=https://api.example.com ./gradlew :app:desktopApp:packageDistributionForCurrentOS
```

If neither is supplied, desktop development falls back to `http://localhost:8080`.

## Web

JS and Wasm clients use an optional `window.QMARKET_API_BASE_URL` runtime override and otherwise fall back to the current browser origin. Production web deployment should normally route `/api/*` to the QMarket server through the same HTTPS origin. Local development may set `window.QMARKET_API_BASE_URL` when the UI and API use different ports.

Local browser development can therefore use the development server's origin when the API is exposed through the same origin. Plain HTTP is accepted only for local hosts by the shared validator.

## Security invariant

Remote `http://` API endpoints are rejected by `validateApiBaseUrl`. Production builds must use HTTPS. This validation is intentionally independent of documentation and fails at application initialization when the supplied endpoint violates the rule.

## Release checklist

- Android Release was built with `-PqmarketApiBaseUrl=https://...`.
- iOS Release was built with `QMARKET_API_BASE_URL=https://...`.
- Desktop production launcher provides `QMARKET_API_BASE_URL=https://...` or `qmarket.api.base-url=https://...`.
- Web is served over HTTPS and `/api` is reverse-proxied to the backend on the same origin.
### Web development override

Before loading `webApp.js`, a deployment may define:

```html
<script>window.QMARKET_API_BASE_URL = "http://localhost:8080";</script>
```

This is suitable only for local development; the shared validator rejects remote plain HTTP.
