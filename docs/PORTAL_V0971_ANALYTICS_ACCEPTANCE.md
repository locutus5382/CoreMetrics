# CoreMetrics v0.9.7.1 — offline analytics acceptance

This iteration is an additive, offline analysis update of v0.9.7. It does not
alter the hardware sampler, recording service, monitor/HUD lifecycle, Quick
Settings tiles, permissions, SQLite schema (v6), or CSV sample columns.

## Before installation

- Preserve the v0.9.7 test APK and back up any recordings that matter.
- v0.9.7.1 versionCode=34; versionName=0.9.7.1-analytics-alpha.
- The debug and optimized-test artifacts can differ in temporary CI signing
  keys. Android may reject an in-place update: do not uninstall until you have
  exported and preserved important sessions.
- Prefer the debug APK for diagnostic testing on an Odin 2 Portal.
- No internet, root, privileged helper, or FPS collection is required.

## Source and CI checks

- Run :app:testDebugUnitTest and assembleDebug, assembleDebugAndroidTest,
  assembleRelease and assembleOptimizedTest through GitHub Actions.
- Check A/B findings are evidence-only: valid discharge comparison, actual
  GPU temperature and utilization differences, never an efficiency winner,
  proof of throttling or game FPS.
- Confirm sparse data, charging, missing sensors and interrupted/partial
  sessions never produce unsupported comparisons.
- Confirm the report-format version remains 0.9.7; application version is
  0.9.7.1. No database migration should occur from the v0.9.7 schema.

## Odin 2 Portal smoke test

1. Finish an ordinary session using existing v0.9.7 controls. Confirm
   standby pill, REC/STOP, Quick Settings, overlay recovery and local export
   behave as before. No new sampling cadence or battery drain is expected by
   design, but any real overhead claim still requires device measurement.
2. Open an existing completed A/B pair (for example, two comparable Hades
   runs). Verify the new "WHAT THESE SELECTED WINDOWS SHOW" observations
   describe recorded temperatures, utilization and qualified battery-terminal
   discharge only. A lower W value never says "better gameplay".
3. Select a shorter A/B interval. Verify the observations reflect only
   the currently selected original samples, not an unmatched tail or
   interpolated data.
4. Use interrupted/partial or gap-containing recordings and compare. Confirm
   the selected-window continuity/gap warning appears, not just the legacy
   composite comparison-input score.
5. Compare sessions with noticeably different initial CPU or GPU thermal
   readings; confirm the UI cautions against thermal-state equivalence.
6. Compare sessions with different last-minute CPU temperature trends
   when enough data are available; confirm these are observational warnings
   rather than claims of a verified physical equilibrium or throttling.
7. Export A/B report; verify the evidence label, observations, warnings,
   selected-window integrity and original source timestamps are coherent.
8. Check a no-new-FPS/no-new-privilege regression: the app measures handheld
   telemetry, not game frame delivery. The only FPS fields supported in
   legacy CSV parsing remain historical, not new measurements.

## Scope deliberately deferred

- Independently offset A/B windows require changes in the range selector,
  chart axis, exports and PNG selection semantics. Not part of this patch.
- Full recording-tick / SQLite write overhead requires a new measured field
  and separate schema change. Not part of this patch.
- Thermal "time to equilibrium", automated GPU bottleneck diagnosis and
  phase-marker collection are not justified by current sensor evidence.
- Instrumentation APK assembly is not equivalent to connected device tests.
- Do not merge into main until user accepts actual Odin behavior.
