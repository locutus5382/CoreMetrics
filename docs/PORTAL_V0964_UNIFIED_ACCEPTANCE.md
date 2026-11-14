# CoreMetrics v0.9.6.4 — Odin 2 Portal acceptance

Branch: `feature/coremetrics-v094-unified-qs` · draft PR #27 · Android versionCode 30 · one application package `com.locutus5382.coremetrics`.

## Installation and preservation
- [ ] Export any important CSVs and diagnostics from v0.9.6.3 *before* attempting to install. Ephemeral Android CI debug signing keys may block in-place upgrades. Uninstalling destroys app-private sessions.
- [ ] Install only one of the v0.9.6.4 APKs; prefer optimizedTest for device realism, debug for a reproducible crash. Do not merge until actual Portal validation.

## STOP, standby and recovery
- [ ] ARM through HUD tile: READY pill opens and performs **no sensor sampling**. While not in the CoreMetrics Activity, expected HUD visible.
- [ ] START via floating REC, record at least 6 samples, STOP: exactly one session finalized, UI and HUD show READY; next REC is explicitly user initiated and opens **a new** session.
- [ ] After STOP, allow or trigger an Android low-memory service restart (not force-stop). READY should NOT spontaneously resurrect from the post-STOP lease. Explicitly tapping the HUD tile may arm a new standby.
- [ ] In a separately armed READY session **without previous STOP**, validated LMK recovery remains allowed (up to existing lease age and restart limits).
- [ ] During active REC an LMK may resume only the **same** existing recording ID. After an explicitly requested STOP any surviving lease may finalize only, not start sampling. Verify journal for SESSION_RESUMED, STOP_RECOVERY_FINALIZE, SESSION_FINALIZED and STOP_INTENT_WRITE_FAILED.
- [ ] Confirm STOP while HUD hidden, DISARM while REC, notification STOP, rapid QS tap and repeated STOP do not create duplicate sessions or overlays. No data are promised during actual process downtime.

## A/B, editing and export
- [ ] Edit a session's graphics driver (Custom T30), scale, note (including accented/Cyrillic text), configuration. Reopen. Verify note has no embedded control characters and current sample count stays unchanged.
- [ ] Edit metadata while detailed analysis is open: selected timeline and report remain; original points are not loaded again. Changing skip-warmup still recalculates the selected analysis.
- [ ] Compare two different sessions: chips show short game/track notes and drivers. The in-app A/B measurement table includes **Busiest core P50**.
- [ ] Export A/B PNG: A and B appear on separate metadata header lines, no text overlaps the table or time range. Review TXT/PNG source footer displays **app v0.9.6.4 / report format v0.9.6**.
- [ ] Both system and Custom tags save, invalid metadata never overwrites valid values and active recording rows are not editable.

## On-demand platform capability probe
- [ ] Export CoreMetrics recorder diagnostics while idle and after Hades; inspect Android thermal status, thermal headroom (may be unavailable), swap used KiB (NOT zRAM physical size) and PSI read status. These are **report-time probes**, not time-series telemetry.
- [ ] Do not attribute past LMK directly to current export-time pressure. Observe logcat and Android's reason-coded process exits to distinguish actual JAVA_CRASH, NATIVE_CRASH, ANR and LOW_MEMORY.
- [ ] No new dangerous permissions, INTERNET, root, interval changes, stored extra sensor fields or CPU/GPU tuning.

## Release gate
- [ ] GitHub testDebugUnitTest, assembleDebug, assembleDebugAndroidTest, assembleRelease and assembleOptimizedTest all pass.
- [ ] On-device Android instrumentation tests must be executed separately; CI compiles but doesn't run them.
- [ ] Run at least a normal 20–30 minute Eden/Hades gameplay recording with finalization, two consecutive REC sessions and session export before considering the next phase.
