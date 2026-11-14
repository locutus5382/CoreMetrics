# CoreMetrics v0.8.7 — Portal acceptance and release gates

**Scope:** Single-Session Analysis Studio only; do not treat this as A/B comparison or a proven throttling diagnostic. The implementation has no hardware polling, no new permissions, and no database schema migration.

## Install / preserve data
- [ ] Save original v0.8.5/v0.8.6 recordings as CSV before deleting old packages.
- [ ] Install v0.8.7 Frosted (app-v087base.apk) alongside v0.8.6. They use separate app IDs, SQLite databases and preferences; import the same CSV to inspect an old recording.
- [ ] Verify Frosted remains fixed original Frosted Glass. No changes to Recorder HUD, REC/STOP, Quick Settings tile, notification or overlay locking.
- [ ] Do not run both variants' recording overlays at the same time.

## Range UI and diagnostics
- [ ] Record a 5–12 minute discharging game session in Eden; stop and open Sessions -> Analyze.
- [ ] Full recording selects the complete original time span; Skip first 30s excludes timestamps before the source first sample + 30s (not guaranteed game-loading completion).
- [ ] Custom range: drag both slider handles, verify highlighted CPU overview, and ensure summary and detailed data change on release. Check 5-second start/end controls using touch and controller/D-pad.
- [ ] Select 2:00–5:00 and verify all statistics use only samples inside those bounds. P50, P95 and peak time must match source CSV values. Cursor must show original elapsed time, not reset to zero at 2:00.
- [ ] Verify CPU/GPU/load/power charts, CPU clocks, observed CPU caps, and technical Classic RAM chart still work for the selected segment.
- [ ] Verify graphs never bridge missing sensor observations or recording gaps. Short segments should show unavailable stability and disabled export, not fabricated zero.

## Stability / power
- [ ] Constant CPU or GPU temperature near the median should produce near-100% valid-time stability. A rapid warm-up can lower it; this does NOT mean thermal throttling.
- [ ] Step volatility reports the mean absolute change per recorded sample step; 2s and 5s samples are not directly comparable.
- [ ] Plug in while recording or import a mixed-power CSV. Charging observations must be absent from battery-discharge stability, power integration and runtime predictions.
- [ ] Verify valid-seconds coverage and gap counts against timestamp differences. Do not interpret stability as a thermal equilibrium detector.

## Segment exports
- [ ] Select a distinct segment and use "Export segment report". Confirm TXT includes original source session ID/status, configuration, absolute selection times, observed sample span, P50/P95/peak, power provenance and stability thresholds.
- [ ] Use "Export segment CSV". Verify header exactly matches regular CoreMetrics CSV schema, timestamps are bounded by selection, no rows are synthesized, and all sensor fields remain present.
- [ ] Import the segment CSV as a separate session; verify it does not mutate, rename or delete the original source session.
- [ ] Test picker cancellation and storage errors; no change to session data.
- [ ] Test an interrupted recording, missing power, a 20s segment, and sessions with gaps.

## Engineering gates
- [ ] GitHub Actions testDebugUnitTest, assembleDebug, assembleV087base and assembleV087themes all pass.
- [ ] Compare exported values against original CSV with an independent calculation. TXT and displayed screen must agree.
- [ ] Confirm responsive interactions for 10+ minute records and acceptable UI with gamepad navigation.
- [ ] Only after acceptance, consider v0.8.8 A/B Studio; keep findings card and PNG for later.
