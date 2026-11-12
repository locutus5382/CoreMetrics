# CoreMetrics

**CoreMetrics** is an independent, offline-first Android gameplay telemetry and session-analysis application, complementary to [O2P Control Center](https://github.com/locutus5382/O2P-Control-Center). Its purpose is to measure and compare *what actually happens* with different CPU/GPU profiles — without modifying them.

**Status:** v0.6 draft experimental HUD/analysis build, stacked on the unmerged v0.4 branch. Not validated on the AYN Odin 2 Portal or approved for Google Play. Source visible for development review only. Copyright (c) 2026 Aleksandar Dukovski. All rights reserved.

## Planned product
- **Live:** CPU/SoC and GPU temperature, CPU/GPU load and clocks, RAM, battery. All readings identified by source, with explicit Unavailable fallback.
- **Sessions:** User-started foreground recording with a persistent Stop notification, local SQLite storage and CSV exports.
- **Compare:** Stock / ClusterTune / Custom run labels, elapsed-time-aligned thermal and utilization charts and differences. A label is user-entered, never proof that a tuner is active.
- **HUD Studio:** Opt-in no-sampling standby capsule, tap REC to start the existing collector, STOP to save and return to standby, plus opacity, size, compact/Pro layouts, metric visibility, mini sparkline, blinking dot and color customization.
- **FPS:** Opt-in experimental SurfaceFlinger per-layer frame-present probe. Standard app UID may lack DUMP permission; a clear error status is shown, never invented FPS. Do not present display refresh or emulator-internal timing as actual delivered game FPS.

## Security, privacy and platform
- Separate Android application ID: `com.locutus5382.coremetrics`. Separate database and notifications; no dependency on O2P.
- No `INTERNET` permission, analytics SDK, account, boot receiver, automatic background recorder, Accessibility service, kernel writes, frequency modifications or `oom_score_adj` changes. Optional `SYSTEM_ALERT_WINDOW` permission is needed only for the user-enabled, single-window HUD.
- Optional Usage Access for identifying app packages. It cannot identify a game running inside an emulator.
- Google Play build baseline uses **compileSdk/targetSdk 36**, minSdk 31. User-initiated `specialUse` foreground recording must be approved by Play Console. Neither target SDK nor a manifest declaration guarantees approval.
- No PServer in initial Play-compatible version. A separate strictly read-only Odin variant may be designed later only if it provides validated value and clear permissions; a read-only observer cannot claim LMK immunity.
- On-device collected readings and session history stay in app-private storage unless the user chooses to export them.

**Parallel test build (recommended):** GitHub Actions now also generates **CoreMetrics Preview**, package `com.locutus5382.coremetrics.preview`. It has a separate provider authority, tile, preferences and SQLite database, so it can coexist with the v0.5 installation even if CI debug signing keys differ. Export v0.5 CSVs and import them inside Preview to compare the same recorded runs. Do not use the regular v0.6 debug APK if you want to preserve v0.5 installed simultaneously. Preview is not an upgrade/migration and will not automatically see v0.5 private sessions.

## v0.6 draft — CSV import, A/B chart scrubber, four HUD graphs, edge dock and FPS Lab

- Four per-metric bounded 30-sample sparklines in the floating Pro HUD: CPU %, GPU %, EMA CPU temp and available layer FPS. No separate sensor loop; standby samples nothing.
- Drag the HUD metric area near a bezel (within 16dp) to collapse it into a 12dp visible tab with 44dp touch target. Tap to expand. The in-game ⚙ drawer controls metric visibility, opacity, graph visibility and REC/STOP. Lock/unlock continues using the notification or Studio.
- Offline, atomic CSV import using the Android Storage Access Framework. Compatible CoreMetrics CSV exports become separate `IMPORTED` sessions with a 12,000-record cap, valid timestamps and headers, and no database migration. Partial/malformed imports roll back. `game_label`, `observed_cpu_limits` and power statuses are preserved.
- Elapsed-time-aligned A/B comparison with CPU/GPU temperatures and loads, battery-terminal power and (when measured) SurfaceFlinger layer FPS. Touch/drag on the graph or use its controller-friendly slider. Nearest point shown within ±10 s; no made-up values across gaps or beyond end.
- FPS Lab is a separately compiled, **not connected, not running** read-only interface/probe state machine with fake-host tests, handshake attestation requirements, unique SurfaceView selection, chosen presentation column, cooldown and two-failure circuit breaker. It does not include a PServer host launcher or privilege adapter. Activating this requires physical Odin read-only host verification and timestamp provenance before implementation.
- No additional Android permissions, no root escalation, frequency writes, user-supplied privileged shell commands, extra background recorder, 1 Hz SurfaceFlinger dumps or data transmission.

**Testing two versions:** Both v0.5 and v0.6 use `com.locutus5382.coremetrics` (same application ID), so they cannot be installed side by side. Test v0.5 first, export CSVs, and then upgrade to v0.6. Android normally blocks downgrading v0.6 to v0.5 without reinstalling and possibly deleting local history.

## v0.5 draft — observed regimes, thermal trends, original icon, touch-lock

- Adds an original CoreMetrics telemetry-pulse adaptive icon, a themed monochrome icon, and proper notification/Quick Settings glyphs.
- The existing single sampling loop drives an EMA (alpha 0.35) CPU HUD value, a raw temperature peak, and a smoothed 15-second trend arrow. Raw SQLite/CSV values are unchanged.
- The **Compare** screen can inspect one previously recorded SQLite session for contiguous *observed* policy3/policy7 CPU ceiling phases, with 1–2 sample glitch debounce, stock/medium/large cap labels, conditional per-phase battery-terminal discharge averages, nominal battery projections (30.8 Wh assumption), and tinted time ranges. It retains A/B two-session comparison.
- HUD touch lock uses `FLAG_NOT_TOUCHABLE` with explicit window alpha 0.70 for Android 12+ pass-through rules. A locked overlay **cannot unlock itself**. Use its foreground-notification Unlock HUD action or the HUD Studio page; the QS tile keeps its REC/STOP role. Other overlapping overlays may still block pass-through.
- Safety: no added polling, no root/PServer/SF probes, no hardware-frequency modification, no invented FPS, no uploaded telemetry or additional permissions. Device validation and CI are required.
- Currently **no external CSV import**: the in-session analyzer reads the existing session SQLite database. If you only have an exported Hades CSV, import is a separate follow-up task.
- Advanced per-metric sparklines, edge-tab docking, a privileged read-only FPS provider, 1% low FPS and verified frame pacing remain separate test-gated tasks. Do not equate FPS inferred from ordinary UI Choreographer callbacks with game FPS.

## v0.4 Pure Cobalt & Solar Glass + standby HUD
- Original static cobalt/orange/yellow light mesh, low-alpha glass surfaces, 1dp specular borders and compact navigation; four real-data circular gauges with unavailable FPS explicitly shown as missing.
- Foreground **standby HUD** is activated by a deliberate user tap, displays a persistent notification, draws one small overlay and runs **no telemetry collector or SQLite recording** until the user taps its REC control. It intentionally displays no fabricated standby measurements.
- REC begins the existing :monitor coroutine and SQLite session; STOP finalizes the session then returns to standby without closing the overlay. Close button or notification exits standby.
- HUD customizer: opacity 30–90%, 3 sizes, compact/Pro layouts, choose CPU/GPU temperatures, CPU/GPU load, RAM, battery net W, optional FPS missing-value indicator, Pro sparkline, red/orange/cyan/violet dot and opt-in blink. Single sample source, no extra polling.
- User must grant Display over other apps permission; ordinary Android background/foreground service restrictions apply, and an Android 14+ `specialUse` foreground-service declaration does not guarantee Play approval.
- Controller-accessible compact 84dp rail retains Live, Sessions, Compare, HUD and Settings pages. O2P Control Center remains unchanged.

## v0.3 stability changes
- Android Quick Settings Record tile remains user-installed; Live and Settings now have an explicit tile-add action (Android 13+).
- Optional small draggable in-game HUD; owned by the active recorder process, no second sampler, detached on Stop. Overlay permission is explicit and OFF by default.
- Fixed navigation clipping and compressed sidebar spacing. New compact status header.
- Known denied SurfaceFlinger FPS probe is **disabled** for this device while an authorized read-only alternative is researched; no child shell subprocesses during recording.
- Recorder uses a safe **2 or 5 second** sampling interval in this build (5 seconds recommended during Eden launch). Previous one-second preference is normalized to 5 seconds.
- No tuning of O2P Control Center, no root privileges. Metroid freeze is not yet attributable to any single component.

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
