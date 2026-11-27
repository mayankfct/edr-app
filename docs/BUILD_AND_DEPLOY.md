# EaglEye EDR/XDR Android – Build & Deployment

Requirements: Android Studio Ladybug+ (bundles JDK 17/21), Android SDK 35. Open this folder in Android Studio; it syncs and creates the Gradle wrapper (or run `gradle wrapper --gradle-version 8.11.1`).

## Configuration
- App ID: `com.forensiccybertech.eagleye` (debug adds `.debug`). Change in `app/build.gradle.kts`.
- Backend URLs: `BASE_URL` per build type in `app/build.gradle.kts`. Placeholders (`*.example.invalid`) must be replaced.
- Version: bump `versionCode` (integer, increases every upload) and `versionName`.
- TLS pinning: add a `<pin-set>` for the production host in `res/xml/network_security_config.xml` before release.

## Builds
| Output | Command | Path |
|---|---|---|
| Debug APK | `./gradlew assembleDebug` | `app/build/outputs/apk/debug/` |
| Release APK | `./gradlew assembleRelease` | `app/build/outputs/apk/release/` |
| Release AAB | `./gradlew bundleRelease` | `app/build/outputs/bundle/release/` |
| Unit tests | `./gradlew testDebugUnitTest` | |

## Release signing
1. Generate a keystore (keep it OUT of git; back it up securely):
   `keytool -genkeypair -v -keystore release.jks -alias eagleye -keyalg RSA -keysize 4096 -validity 10000`
2. Copy `keystore.properties.example` to `keystore.properties` and fill in values (git-ignored).
3. Build release. Without `keystore.properties`, the release APK is unsigned and not installable.
4. For Play Store, enrol in Play App Signing and upload the AAB.
5. Verify: `apksigner verify --print-certs app-release.apk`.

## Security design
- Token encrypted with AES-256-GCM, non-exportable Android Keystore key; backups disabled.
- HTTPS only, system CA trust, no cleartext.
- `ReadOnlyGuard` blocks every non-GET request except the auth POST, locally (unit-tested).
- 401 with a stored token wipes the token and returns to login ("session expired").
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE` only. No notifications or background services.
- Release: R8 minification and resource shrinking enabled.

## Authentication (native, central auth server)
The user signs in inside the app (email or username + password); no browser or WebView. The app calls the same API as the web login page:

| Call | Details |
|---|---|
| Login | `POST https://auth.eagleyesoc.ai/api/auth/login` body `{"username","password","returnTo"}`; header `Origin: https://auth.eagleyesoc.ai` is **required** (403 without it, verified). Wrong credentials: `401 {"error":"Invalid username or password"}` (verified). Success: server sets session cookies (not yet verified live). `forced_change:true` in the response is treated as "contact administrator". |
| Refresh | API call to vanguard returns `401 {"action":"refresh_required"}` → `POST /api/refresh` on the auth host, then the call is retried once. |
| Logout | `POST /api/logout`, then local cookies are wiped. |

Cookies are kept in an OkHttp `CookieJar` persisted encrypted (Android Keystore). Any other 401 or a redirect from vanguard expires the session and shows the login screen. The read-only guard permits only GETs plus those three auth POSTs. MFA/OTP is not handled (deferred).

## API (read-only, GET)
| Screen | Endpoint |
|---|---|
| Alerts list (paged, pull-to-refresh) / detail | `GET /api/v1/alerts?page&size&sort_by=@timestamp&sort_order=desc` |
The alert parser accepts a bare array, `{alerts|data|items|results|hits: ...}` or OpenSearch `hits.hits[]._source`, and both real JSON arrays and JSON-in-string values (as in the OpenSearch CSV export). **Verify against a live response** – the real envelope is unconfirmed. Field mapping: see `data/Alert.kt`.

## Screens and endpoints (all GET, read-only)
Shapes were taken from the web console's frontend code; verify against live responses.
| Screen | Endpoints |
|---|---|
| Dashboard: KPIs, severity donut, 7-day volume, heatmap, response actions, categories, top rules/devices/MITRE/users/tags, recent alerts | `/api/v1/stats/summary`, `/api/v1/stats/top`, `/api/v1/stats/timeline?period=7d&interval=1h`, `/api/v1/alerts` |
| Alerts: severity/category chips, process-name search, paging, detail | `/api/v1/alerts?page&size&sort_by&sort_order&severity&process_name&event_category` |
| Devices: totals, seats, online/offline, isolated badge, search | `/api/v1/hosts` |
Not included: vulnerabilities (`/api/v1/vulns*`), rules/groups, notifications, and any action (isolate, uninstall...).

## Status
Source complete for login + alerts; **not yet built or tested** (no Android SDK on the dev machine). Pending: stats/hosts/groups screens (need endpoint + sample JSON), test evidence.
