# CoreMetrics

**CoreMetrics** is an independent, offline-first Android gameplay telemetry and session-analysis application, complementary to [O2P Control Center](https://github.com/locutus5382/O2P-Control-Center). Its purpose is to measure and compare *what actually happens* with different CPU/GPU profiles — without modifying them.

**Status:** v0.2 experimental test build; not yet validated on the AYN Odin 2 Portal, not Play Store approved. Source visible for development review only. Copyright (c) 2026 Aleksandar Dukovski. All rights reserved.

## Planned product
- **Live:** CPU/SoC and GPU temperature, CPU/GPU load and clocks, RAM, battery. All readings identified by source, with explicit Unavailable fallback.
- **Sessions:** User-started foreground recording with a persistent Stop notification, local SQLite storage and CSV exports.
- **Compare:** Stock / ClusterTune / Custom run labels, elapsed-time-aligned thermal and utilization charts and differences. A label is user-entered, never proof that a tuner is active.
- **HUD Studio:** Future opt-in overlay and editable views after recording reliability is established.
- **FPS:** Opt-in experimental SurfaceFlinger per-layer frame-present probe. Standard app UID may lack DUMP permission; a clear error status is shown, never invented FPS. Do not present display refresh or emulator-internal timing as actual delivered game FPS.

## Security, privacy and platform
- Separate Android application ID: `com.locutus5382.coremetrics`. Separate database and notifications; no dependency on O2P.
- No `INTERNET` permission, analytics SDK, account, boot receiver, automatic background recorder, Accessibility service, kernel writes, frequency modifications or `oom_score_adj` changes.
- Optional Usage Access for identifying app packages. It cannot identify a game running inside an emulator.
- Google Play build baseline uses **compileSdk/targetSdk 36**, minSdk 31. User-initiated `specialUse` foreground recording must be approved by Play Console. Neither target SDK nor a manifest declaration guarantees approval.
- No PServer in initial Play-compatible version. A separate strictly read-only Odin variant may be designed later only if it provides validated value and clear permissions; a read-only observer cannot claim LMK immunity.
- On-device collected readings and session history stay in app-private storage unless the user chooses to export them.

## v0.2 testing features
- Battery net watts measured from Android battery current and voltage, with provenance and charging warning. Not CPU/package or wall power.
- Optional SurfaceFlinger per-layer present-rate probe (off by default; often permission denied on Android 13). Does not measure internal emulated-game FPS.
- Durable recorder lifecycle journal and app-specific Android process exit diagnostics, plus non-destructive SQLite v1 to v2 migration preserving saved history.
- Observed CPU policy ceiling readouts are separate from user-entered Stock/ClusterTune labels and never claim the identity of a tuning app.
- Updated Instrument Glass UI: real metric cards and 60-second live chart using existing session readings, not extra sampling.

## Build
JDK 17, Android SDK 36, Gradle 8.11.1, Android Gradle Plugin 8.10.1.
The source does not include a binary Gradle wrapper jar; CI provisions Gradle explicitly.
```bash
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
```
The GitHub Actions **Android debug build** workflow compiles and runs JVM tests, then uploads `app-debug.apk`.

See [development scope](docs/ROADMAP.md), [Play preparation](docs/PLAY_RELEASE_CHECKLIST.md), and [privacy statement](PRIVACY.md). Do not distribute this development build as a finished commercial app.

## Copyright
Copyright (c) 2026 Aleksandar Dukovski. All rights reserved. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
