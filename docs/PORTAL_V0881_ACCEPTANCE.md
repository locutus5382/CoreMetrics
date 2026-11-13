# CoreMetrics v0.8.8.1 — Odin 2 Portal acceptance tests

## Install and preservation
- Install v0.8.8.1 Frosted (app-v0881base.apk) next to previous versions. The app ID/database are independent. Transfer previous sessions via original CSV export/import.
- Verify both Frosted and Themes APKs build, and that the base variant remains original Frosted Glass.
- Avoid running more than one CoreMetrics recorder overlay at a time.

## Best-effort recovery, no forced continuation
- Start REC from Quick Settings or standby, set sampling to 5 seconds, open Eden and lock the HUD.
- Confirm the separate unlock chip works and all other HUD touches pass through to the game while locked.
- Play for 10–20 minutes with realistic memory load. If Android terminates the :monitor process, examine Recorder Diagnostics immediately and note whether Android restarts the foreground service automatically without a tile tap.
- On eligible automatic restart, the SAME session ID should be resumed, source data preserved, and the locked HUD restored, including the unlock chip. Missing recording time remains a real gap (no interpolation).
- If Android never restarts a low-memory-killed process, record that fact; START_STICKY is only a request and cannot defeat OEM or platform memory policy.
- Explicit STOP or DISARM MUST NOT auto-restart. Start via Quick Settings after an unrecovered kill MUST start a separate new session and mark the previous one interrupted.
- DO NOT use Android Settings 'Force stop' as a recovery test: user-forced stopping suppresses service restarting by design.

## HUD UI
- Select CPU temperature, GPU temperature, CPU%, GPU%, RAM, battery W; inspect all six values in 2–3 lines without GPU... truncation. Test three HUD sizes.
- Open CoreMetrics while actively recording. Overlay should pause on the app screens, not block app header/settings. REC continues.
- Switch back into Eden. Previously user-enabled HUD should reappear. Explicit user-hide via QS tile must NOT be overwritten on leaving CoreMetrics.
- Lock/unlock/dock/expand/STOP and tap the HUD drawer settings; no orphaned 48dp unlock chip or overlapping app-header HUD.
- Standby mode must not sample or write rows until REC starts.

## Diagnostic exports
- Recorder journal heading shows v0.8.8.1 from installed package, not hardcoded v0.8.5.
- Single-session report no longer calls flat thermal readings 'steady-state'. Slope still shown as observed trend; no thermal soak detector.
- Nominal-capacity battery-runtime estimate is collapsed by default and explicitly low confidence; no derived game FPS or fake stutter conclusions.
- A/B scorecard, selected-time crop and exports remain functional. Compare selected TXT and CSV with the original data for sample counts, gaps and power.

## Engineering release gate
- testDebugUnitTest, assembleDebug, assembleV0881base, assembleV0881themes all green, with artifacts matching final commit.
- Save new Recorder Diagnostics file if another spontaneous disappearance occurs; the Android exit reason and journal will guide future stabilization.
