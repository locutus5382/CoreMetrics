# CoreMetrics v0.9.4 — Unified Odin 2 Portal acceptance

**Branch:** `feature/coremetrics-v094-unified-qs`. One canonical application ID: `com.locutus5382.coremetrics`. This is an alpha until Android CI and on-device regression pass.

## Build and migration
- [ ] GitHub Actions testDebugUnitTest, assembleDebug and assembleDebugAndroidTest succeed; only one `app-debug.apk` artifact is uploaded.
- [ ] The APK contains the `com.locutus5382.coremetrics` package without `.v093themes`, `.v093base`, `.preview` or `.v07test` suffix build types.
- [ ] Export important private session data from every old test package before removing it. Install the canonical APK with a compatible signing key or back up and explicitly migrate CSVs. **Uninstalling a package permanently removes its private SQLite sessions.**
- [ ] Remove stale QS tiles for the old packages and revoke their overlay grants. Confirm only one installed CoreMetrics variant is active during gameplay.

## Native Quick Settings visuals and semantics
- [ ] **CoreMetrics HUD** has a thin outlined pulse/circle when OFF, bold pulse/circle when ON; verify native `STATE_INACTIVE` vs `STATE_ACTIVE` fill on the Portal OS.
- [ ] Labels are stable: CoreMetrics HUD / CoreMetrics REC. Subtitles describe Off, Ready, REC with HUD visible/hidden, Recover and Starting. Long-pressing HUD opens the HUD Studio. System accent and two-line display remain OEM-controlled.
- [ ] HUD QS tile **never** stops an in-progress recording; when recording is hidden, the HUD tile turns inactive but text explicitly says REC is active. The separate REC tile starts/stops recording.
- [ ] Tap both tiles rapidly during startup, while recording, while hiding/showing, and after a recreation. No duplicate overlays, new session IDs, orphan buttons or silent restarts.
- [ ] Recovering a validated persisted recording after process exit uses its **original session ID**. A cold tile-only process has no service, so it must read a fresh lease before treating it as idle.

## Theme convergence
- [ ] Frosted Glass remains the default for fresh installs and saved `ui_surface_finish` preferences persist across Activity restarts.
- [ ] All four finishes appear in the same Settings / STYLE picker. Neither theme choice nor landscape UI transitions affect recorder state, HUD position, touch lock, CSV/PNG outputs or permission model.
- [ ] Verify True Glass renders legibly. No forced full-screen blur, perpetual animations or extra per-frame shaders.

## Efficiency and safety
- [ ] With ADB inspect `dumpsys meminfo com.locutus5382.coremetrics` and `dumpsys meminfo com.locutus5382.coremetrics:monitor` in Idle, Standby and REC. Compare PSS on equivalent scenes, not previous single-shot exit-history values.
- [ ] In idle UI, no telemetry/sampler thread starts; UI status polling backs off to 8 seconds. Live/HUD polls while active at ~1.8 seconds, other pages ~4.5 seconds.
- [ ] A tile opened in Quick Settings need not launch the Compose UI process. No extra ContentProvider IPC from the tile to the same `:monitor` process.
- [ ] Thermal values and GPU/CPU state match earlier good runs within normal scene variability. No sensor-path misclassification after caching.
- [ ] Change the optional **Close this screen after launching standby** toggle and verify user choice; finishing Activity is not an immediate guarantee of 102 MiB reclaimed.

## Release gate
A green CI run verifies syntax/unit and APK packaging only. Complete real-device Hades testing of single HUD, Quick Settings behavior, 30+ minute REC, interruptions, STOP, exports, and the ability to recover accurately before merging into main or calling this a stable public release.