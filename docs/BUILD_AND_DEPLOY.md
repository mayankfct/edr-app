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
| Vulns: KPIs (open/patched/KEV/hosts/MTTP), severity donut, priority bars, top CVEs, findings list with severity filter + paging | `/api/v1/vulns/summary`, `/api/v1/vulns/stats/top-cves`, `/api/v1/vulns?limit&offset&severity` (severity value case is unverified: uppercase is sent) |
Alerts also has a host dropdown (`agent_id` filter) and a search box with suggestions (users from `stats/top` + loaded alerts, and process names). Picking a user filters the loaded alerts on the device, because the API only filters by process name. Devices search suggests hosts, users and IPs.

Design: the UI follows the "EaglEye Sentinel" design system from the designer's Stitch export (navy surface layers, cyan primary, severity pills, monospace forensic blocks with tap-to-copy, 4-tab bottom bar, LIVE pill header). Tokens are in `ui/theme/Theme.kt` (`EagleColors` holds what Material3 has no slot for); shared components in `ui/components/Widgets.kt`. Fonts: Roboto Flex and JetBrains Mono from the design are not bundled; the app uses the system sans font and the system monospace. Not implemented from the mockups: tablet dual-pane, the mic button in search, risk-score sorting (no data), and the decorative claims shown in the login mockup (FIPS/SOC2/biometric).

Appearance: Theme (System / Light / Dark) in the top-bar menu, remembered across launches. The login screen recreates the Vanguard sign-in look natively (logo copied from auth.eagleyesoc.ai); sign-in still happens inside the app.

Not included: vulnerability inventory/apps/scans, custom rules/groups, notifications, and any action (isolate, uninstall...).

## Status
Source complete for login + alerts; **not yet built or tested** (no Android SDK on the dev machine). Pending: stats/hosts/groups screens (need endpoint + sample JSON), test evidence.
