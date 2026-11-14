# CoreMetrics v0.9.6.6 — Odin 2 Portal acceptance

**Branch**: `feature/coremetrics-v094-unified-qs` · draft PR #27 · versionCode 32 · package `com.locutus5382.coremetrics`. CI passing does not establish Portal runtime reliability.

## Before installation
- [ ] Export all important CSV sessions and previous diagnostic text reports first. GitHub CI builds may use a new temporary debug signing certificate. A signing mismatch may require uninstall and erase the existing private database.
- [ ] Install only the debug **or** optimizedTest APK (prefer R8 optimizedTest for normal test). Do not merge to `main` until hardware acceptance.

## Recording / HUD nonregression
- [ ] Run a fresh Eden/Hades session: HUD READY → REC → at least 12 samples → STOP. Confirm one stored SQLite session and READY, with no duplicate overlay, no accidental second recording.
- [ ] Launch a second session explicitly and repeat; re-open Sessions to verify both counts and metadata.
- [ ] Use REC/HUD Quick Settings tiles while standby, while recording and when HUD hidden. No surprise reactivation after STOP, no duplicate REC; capture lifecycle report for any failure.
- [ ] Test right/left/top docking, drawer toggle frosted ON/OFF, HUD lock/unlock and manual resize with 2/4/6 enabled metrics.
- [ ] Try longer gameplay to observe potential LMK behavior; downtime remains a real gap in the data, no automatic claim of uninterrupted monitoring.

## Session analysis / metadata
- [ ] Open a finished session and inspect quality score + detail timeline.
- [ ] Change the configuration from Stock to Underclocking using **Edit**; verify derived label-conflict/quality information updates while original sample count stays unchanged.
- [ ] Change custom graphics driver, resolution scale and game note; verify cached timeline stays in place and new metadata appears in the analysis.
- [ ] Confirm note editor visibly shows **Saving…** → **Game note saved** or error; repeated taps while saving should do nothing.
- [ ] Change skip-warm-up: analysis recomputes correctly from existing samples; metadata is not overwritten.
- [ ] If the same selected recording gains samples after REC/STOP and the history is refreshed, its sample count triggers reloading of the original points. A metadata-only edit must not do so.
- [ ] Compare two sessions; verify the in-app Busiest Core P50 row, game notes in A/B selection and 2-line metadata in exported PNG.

## Diagnostic probe and exports
- [ ] Settings → View process-exit & lifecycle log. Button displays **Reading diagnostics…** until complete, and Copy is disabled while pending.
- [ ] Copy report idle, then after a short Hades run. Verify readable timestamp, OS thermal state name/code, headroom (may be unsupported), swap Total/Free/Used, memory PSI and CPU policy7 time_in_state status.
- [ ] Confirm unavailable kernel sources are clearly unavailable and don't crash the app. These are instant report-time samples; don't infer conditions at an earlier LMK.
- [ ] A/B PNG/TXT reports should display **app v0.9.6.6** and separately **report format v0.9.6**; no invented game FPS, fan speed, CPU watts, or extrapolated data.

## Release gates
- [ ] JVM unit tests and all Gradle APK assembly jobs succeed on the exact release head. Android instrumentation APK compiles, but those device tests are NOT executed in GitHub CI.
- [ ] Physical Odin device behavior confirms nonregression. Keep PR draft until screenshots, test session exports and diagnostic report are reviewed.
