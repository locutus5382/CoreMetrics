# CoreMetrics v0.9.6 — unified Portal acceptance

This is a single canonical-package alpha built on the v0.9.4 unified branch and previous v0.9.3 lifecycle fixes. Do not merge to main or label stable until a real 8GB Odin 2 Portal run and data migration succeed.

## P0: Recording safety and race
- [ ] Arm via app with Close screen after launching standby OFF, then ON. Under ON, overlay shows after Activity closes; no persistent HUD_PAUSED_IN_APP, no duplicated control and no accidental recording
- [ ] Long press both Android Quick Settings tiles, hide HUD while REC, show again; REC continues on the same session ID, sampling is never duplicated
- [ ] In Eden/Hades, run >=30 minutes with alternate drivers only when the gameplay scene/fan settings are consistent. Compare process RSS/PSS using dumpsys meminfo for UI and :monitor with similar runs
- [ ] Kill / resume process only in a disposable test. Unknown/Recoverable must NOT start a new recording; recovered session must preserve ID and an honest gap. Force-stop is not LMK

## P0: Database integrity and migration
- [ ] Back up CSVs in old .v093base/.v093themes/.preview variants BEFORE uninstalling. The unified canonical package does not import private SQLite automatically; debug keys must match for in-place upgrade
- [ ] Create a short completed test recording, edit game note and profile after Quick Settings REC, tag driver and scale, export CSV/TXT and verify metadata fields without any original sample changes
- [ ] Delete the short completed recording only after the confirm screen; all samples and A/B links to it disappear; another recording and its samples remain untouched
- [ ] Confirm deletion of a currently RECORDING session is refused even with stale Activity status; data persists when delete is cancelled
- [ ] Export an old session ID beyond the most recent 200 sessions without an unrelated list() failure (a stress test with a backed-up disposable database)

## P1: Diagnostics and charts
- [ ] A/B PNG has 8 visible metric rows including Busiest core P50, with B − A signs correct, legible spacing and no row colliding with footer/graph
- [ ] A/B scrubber shows busiest core, P3 and P7 only if source values exist. Observe clock as a snapshot, not a governor cap or guaranteed game FPS
- [ ] Session timeline and A/B graph render on all four themes with original gaps disconnected, without stale traces after metric switches; rapid scrubbing remains responsive
- [ ] Export current and old sessions with correctly identified note, driver, configuration. PNG reports remain professional without Ko-fi promotion

## P1: Optional support and release build
- [ ] Settings Open Ko-fi sends https://ko-fi.com/locutus5382 to a browser, Copy link works without Internet permission or external SDK. No features are locked, no subscriptions
- [ ] README and FUNDING.yml point to the same volunteer link; no donation link on PNGs
- [ ] CI testDebugUnitTest, assembleDebug, assembleDebugAndroidTest and R8 minified assembleRelease succeed. Release APK is unsigned until proper production signing
- [ ] Test R8 release candidate with notifications, tiles, recorder, CSV import/export, graphics and Activity recreation on a clean test device

## Main integration gate
Only one active integration PR. Main remains unchanged until the device passes and a safe install/migration strategy is documented.
