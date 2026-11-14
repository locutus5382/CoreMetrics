# Odin 2 Portal acceptance — CoreMetrics v0.9.6.2

Current unified development branch: `feature/coremetrics-v094-unified-qs` / draft PR #27. Android versionCode 28, versionName `0.9.6.2-unified-alpha`. Both artifacts use the same application ID; **only one installed at a time**. Debug and optimizedTest may have different signing identities across CI runners, requiring backed-up uninstall. Never uninstall a build holding irreplaceable unexported sessions.

## Packaging checks
- [ ] CI: `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease` and `assembleOptimizedTest` all pass.
- [ ] Obtain and measure both APKs, and compare with the 53.1 MB v0.9.6.1 debug baseline. Record signed optimized size separately. No claims about PSS or cold-start improvements from size alone.
- [ ] Test minified app **on the Portal**, not just the unminified debug build: launch all four themes, charts, save PNG/CSV, optional support link, QS HUD/REC and notification actions. The release minifier might behave differently from debug.
- [ ] Confirm no INTERNET permission, no added native libraries, no paywall or support links in PNGs.

## Sampler and lifecycle
- [ ] Check CPU, GPU and battery readings against v0.9.6.1 in comparable conditions. Ensure unavailable sensors stay unavailable instead of reporting zero.
- [ ] P3/P7 observed `scaling_max_freq` reflects a separate user-installed tuner even though hardware `cpuinfo_max_freq` is cached. This app never changes clocks or voltages.
- [ ] Check first sample and gaps on start, 5s cadence (or 2s if explicitly selected), after leaving the app, when running demanding Hades/Eden. Measure delayed samples rather than assuming a fixed 40–60% CPU reduction.
- [ ] Standby has no sampling; REC uses one session ID; after stop, completed sample count and original timestamps survive a service/process lifecycle event.
- [ ] Test QS HUD/REC and notification STOP repeatedly. Overlay must remain single and interactive after game launch, app foreground/background, collapse, lock/unlock and low-memory pressure.
- [ ] For low-memory restart, recover **only** the same fresh recording lease and ID. No spontaneous new session, duplicate HUD or foreground service creation. Android can still kill and decline to restart an app.
- [ ] Verify user STOP does not automatically restart, even if recording finalization encounters SQLite error. Inspect recorder journal for `SESSION_FINISH_DB_ERROR`.

## A/B and data integrity
- [ ] Import the existing Hades T30 and StevenMXZ CSVs. Test selecting A, selecting B, swapping twice; the B−A score reverses consistently while original samples and metadata do not change. The swap should not trigger a new SQLite `points()` call.
- [ ] Change one side, switch away from Compare and back, cancel SAF picker. Confirm no mixed/stale samples; cached analyses are released when appropriate.
- [ ] Export 8-row A/B PNG and check P3/P7/Busiest Core, precise notes, no row clipping and no Ko-fi branding.
- [ ] Back up before any deletion. A RECORDING row and any fresh pending lease must remain undeletable.
- [ ] Exercise v4→v5 SQLite migration with existing sessions. Instrumentation test compilation in CI is **not** execution on a device.

## Memory / energy measurement (8 GB Portal)
- [ ] Record `adb shell dumpsys meminfo com.locutus5382.coremetrics` and `adb shell dumpsys meminfo com.locutus5382.coremetrics:monitor` at idle, armed with UI closed, after 10 minutes Hades REC, and after STOP. Note available RAM and any process exits.
- [ ] Compare both builds only under similar device/game/driver/thermal circumstances. Capture sample count, actual sampling gaps, UI PSS and monitor PSS. No inferred game FPS if SurfaceFlinger access is denied.
- [ ] If a runtime crash or lost HUD occurs, export recorder diagnostics and Android logcat; avoid merging until reproducible issues are resolved.

**Release gate:** Only after on-device validation does this branch become a suitable `main` merge candidate. The CI-optimized APK is a test artifact, not a signed Play Store release.
