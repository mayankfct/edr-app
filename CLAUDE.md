# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Native Android client (Kotlin, Jetpack Compose, MVVM) for the EaglEye EDR/XDR "Vanguard" console. **Strictly read-only in this release**: no endpoint-management or response actions, no background services, no notifications. Do not add iOS/Flutter/RN/web substitutes. Package `com.forensiccybertech.eagleye`; `minSdk 26`, `compileSdk/targetSdk 35`, Java/Kotlin 17.

## Commands

Run from the repo root (Windows: `gradlew.bat`). The wrapper jar is not committed; Android Studio regenerates it, or run `gradle wrapper --gradle-version 8.11.1`.

- Debug APK: `./gradlew assembleDebug`
- Release APK / AAB: `./gradlew assembleRelease` / `./gradlew bundleRelease` (needs `keystore.properties`, copy from `keystore.properties.example`; without it the release APK is unsigned)
- All unit tests: `./gradlew testDebugUnitTest`
- Single test class / method: `./gradlew testDebugUnitTest --tests "com.forensiccybertech.eagleye.StatsParserTest"` (append `.hosts` for one method)

No lint/format tooling is configured. Build and signing details are in `docs/BUILD_AND_DEPLOY.md`.

## Architecture

Manual DI: `EagleEyeApp` (Application) builds and exposes `session`, `api`, `auth`, `theme`; ViewModels take the `EagleEyeApp` via `viewModelFactory { initializer { ... } }`. No Hilt/Koin.

**Auth and networking is the part that spans several files and must be read together:**
- Login is native (no WebView/browser, a product requirement). `AuthRepository` POSTs `{username, password, returnTo}` to `https://auth.eagleyesoc.ai/api/auth/login`; the central auth server sets session cookies. Requests to the auth host **must carry `Origin: https://auth.eagleyesoc.ai`** (else 403), which `authPost()` in `network/ApiClient.kt` adds.
- There is no token in app code: cookies live in `EncryptedCookieJar` (OkHttp `CookieJar`), persisted encrypted by `SecureTokenStore` (AES-256-GCM key in Android Keystore). `SessionManager` exposes `LoggedOut/LoggedIn/Expired` as a `StateFlow`; `MainActivity` switches between `LoginScreen` and `MainScaffold` on it.
- `ApiClient` interceptor order matters: `ReadOnlyGuard` (blocks every method except GET/HEAD, and POST only to the auth host paths `/api/auth/login`, `/api/refresh`, `/api/logout`; returns a synthetic 405 locally) then `SessionInterceptor` (on 401 with `{"action":"refresh_required"}` calls `/api/refresh` once and retries; any other 401 or a redirect from the app host calls `session.expire()`). Redirects are disabled on purpose.
- Any new endpoint must be GET to be allowed. Adding a write/action call means changing `ReadOnlyGuard` and its test deliberately.
- Hosts: API on `vanguard.eagleyesoc.ai` (`BuildConfig.BASE_URL`/`APP_HOST`), auth on `auth.eagleyesoc.ai` (`AUTH_HOST`); both debug and release point at production. Config lives in `app/build.gradle.kts`.

**Data layer:** Retrofit services (`network/AlertsService.kt` holds Alerts/Stats/Vulns/Hosts) return raw `ResponseBody`; JSON is parsed by hand-written tolerant parsers (`data/Alert.kt` `AlertParser`, `data/Stats.kt` `StatsParser`, `data/Vulns.kt` `VulnParser`), not generated serializers. They accept both real JSON and the OpenSearch-export style (JSON-in-string arrays, comma-formatted numbers) and degrade missing fields to `""`/0.
- **Response shapes were reverse-engineered from the web console's frontend JS and a Postman collection, not from API docs, and have not all been verified against live responses.** The alerts envelope is guessed (`AlertParser` tries several wrappers). Treat parser mismatches as the first suspect for "empty widget" bugs.

**UI:** `ui/MainScaffold.kt` hosts the bottom-nav tabs (Dashboard, Alerts, Devices, Vulns) and the overflow menu (theme, sign out). Each tab is one file with its ViewModel and screen (`ui/dashboard/DashboardTab.kt`, `ui/alerts/AlertsTab.kt`, `ui/hosts/HostsTab.kt`, `ui/vulns/VulnsTab.kt`). The visual design comes from the designer's "EaglEye Sentinel" Stitch export (tokens in `ui/theme/Theme.kt`: use `Eagle.colors` for card/border/severity colors, `MonoSmall` for hashes/IPs/PIDs/commands, and `SeverityChip`/`MonoBlock` instead of ad-hoc styling). Charts are hand-drawn with Compose `Canvas` in `ui/components/Widgets.kt` (no chart library). `ui/components/AppHeader.kt` is the shared top bar (there is no Material `TopAppBar`). `SuggestField`/`PickerField` are the reusable dropdown inputs. The login screen is always dark; the rest follows `ThemeStore` (System/Light/Dark).

## Conventions specific to this repo

- Dashboard widgets load independently and fail independently (`safe {}` in `DashboardViewModel`); keep that pattern for new widgets.
- The alerts API only filters server-side by severity, process name, agent id, category and time; the "user" filter is applied client-side on loaded alerts.
- Permissions are limited to `INTERNET` and `ACCESS_NETWORK_STATE`; backups are disabled and cleartext is off. Don't widen these without a reason.

## Status

The code has never been compiled locally (no Android SDK on the original dev machine); a teammate builds it. Expect to verify real login (cookie scope across auth/vanguard hosts, `returnTo` acceptance), live response shapes, and MFA (unsupported) against a real account.
