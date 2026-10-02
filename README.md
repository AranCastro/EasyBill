# EasyBill (working name; app display name: Kounter)

Offline-first billing and inventory app for Android, for GST and non-GST
shops in India. Kotlin + Jetpack Compose + Material 3. Zero running cost: all
data stays on the phone.

- Project plan: [docs/easybill_project-plan_v2.md](docs/easybill_project-plan_v2.md)
- Status: **Phase 0 complete** (project skeleton, design system, dashboard, CI)

| Light | Dark | Empty state |
|---|---|---|
| ![Dashboard light](docs/screenshots/dashboard_light.png) | ![Dashboard dark](docs/screenshots/dashboard_dark.png) | ![Dashboard empty](docs/screenshots/dashboard_empty.png) |

## Build

Requirements: Android Studio (latest stable) or JDK 21 + Android SDK (platform 37).

```bash
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest :core:common:test   # unit + screenshot tests
```

Screenshot tests write PNGs to `feature/dashboard/build/outputs/roborazzi/`.

## Modules

| Module | Purpose |
|---|---|
| `app` | Activity, navigation, bottom bar |
| `core:model` | Plain Kotlin models (`Money` in paise, transactions) |
| `core:common` | Indian currency formatting, amount in words |
| `core:designsystem` | Theme, colours, Inter font, reusable components |
| `feature:dashboard` | Home dashboard |

## Licences

Inter font: SIL Open Font License 1.1 (`core/designsystem/FONT_LICENSE_Inter.txt`).
