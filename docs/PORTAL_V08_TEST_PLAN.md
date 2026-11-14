# CoreMetrics v0.8 — A/B feature and theme tests on Odin 2 Portal

## Install two separate applications
- Analytics-only: `CoreMetrics-v0.8-analytics-only`, file `app-v08base.apk`, package `com.locutus5382.coremetrics.v08base`
- Optional UI Themes: `CoreMetrics-v0.8-themes`, file `app-v08themes.apk`, package `com.locutus5382.coremetrics.v08themes`
- Both preserve the v0.6/v0.7 installations; neither inherits the other app's SQLite. Export CSV from earlier version, import in either v0.8. Never enable both recording services or HUDs simultaneously.

## Naming and source preservation
1. Start a new 10–20 s recording after selecting Stock, Underclocking or Custom and entering an optional game note. The saved label must be `Session N` with monotonically increasing SQLite ID, not NFS benchmark. The note must remain separate.
2. Import a historical v0.6/v0.7 CSV; verify a numbered session, original game/track note, raw samples, original source status and 26-field CSV export. No changes to original source file.
3. Test v2→v3 SQLite migration with a copy of v0.7 data in an upgrade-compatible installation, separately from the side-by-side v0.8 packages. It must preserve all raw telemetry and statuses.

## Four analytics refinements
4. In Compare, select your two Mario Kart runs, then **Link these recordings**. Actual timestamps and an approximately 24.2 s unrecorded gap should appear; statuses remain interrupted/completed. Tap **Unlink recordings**; both sessions must remain accessible and unmodified. Never call a manual link automatic recovery.
5. Observe `Selected: Stock` and the different `Observed: Large UC (1.79 / 1.84 GHz)` phase. A recording that switches ceilings should show sequential phase windows, without rewriting the user label.
6. Check 8-core winner bar: CPU0 may dominate the 72-sample Mario Kart history; percentages must sum to ~100% over valid winner observations, excluding missing cores. It is NOT CPU core residency.
7. Validate total battery-terminal Wh, normalized mWh/min, time-weighted W and valid-interval discharge coverage. For supplied sessions rough expectations: ~0.029 Wh / 57.8 mWh/min / 3.47 W; ~0.161 Wh / 68.1 mWh/min / 4.08 W. Match integrated intervals; charging or large gaps must not be interpolated.
8. Compare reports should caution on unequal durations, GPU load changes, incomplete energy data and manual label mismatches. Missing game FPS must stay unavailable.

## UI style tests (Themes app only)
9. Open Settings > App Appearance. Default should be visually identical to baseline Frosted Glass. Select Liquid Glass, then Tactical Flat; inspect the entire landscape display and scroll behavior.
10. Check small text contrast, buttons and D-pad navigation, readability over the game-independent app background, temperature arcs and correct color meaning. Verify no blurred text, frozen animations, accidental rec buttons, HUD changes or FPS claims.
11. Switch back to Frosted. Kill/reopen app and verify selected style persists, while Analytics-only remains frozen to Frosted. Check no extra memory/CPU churn in either style.

## Stability gate
12. Game-first launch, lock/unlock with separate chip, in-game recording/stop, edge-dock and Android low-memory recovery: unchanged from v0.7; record any regression. Stop testing if the Odin or Eden freezes. No OOM priority manipulation and no privileged FPS access.
