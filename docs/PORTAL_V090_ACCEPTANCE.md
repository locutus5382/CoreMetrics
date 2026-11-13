# CoreMetrics v0.9.0 — Odin 2 Portal acceptance

## Install and basic regression
- [ ] Install v0.9.0 Themes APK (app-v090themes.apk) and optionally Frosted (app-v090base.apk). Distinct application IDs/SQLite databases: import previous CSVs. Never run two HUD overlays at once.
- [ ] Run Eden with native HUD recording, lock/unlock, stop/notification and Quick Settings. Verify previous v0.8.8.1 best-effort LOW_MEMORY recovery behavior; no v0.9.0 change to sampling/recorder.
- [ ] Confirm Live, Sessions, Compare, HUD and Settings remain navigable via touch and controller; no visual regressions from v0.8.9.

## Full and selected single-session PNG
- [ ] Sessions → Analyze → Full recording → Save PNG scorecard. Inspect file in Android Files/Gallery or transfer to computer; must be a valid 1600×1050 PNG.
- [ ] Select 02:00–05:00, export TXT and PNG and compare original elapsed range, CPU/GPU P50/P95, discharge and sample counts. PNG must not show values outside selected interval.
- [ ] Missing temperature or real recording gaps must cause discontinuities in chart, not synthetic line interpolation. Missing power must be unavailable rather than zero.
- [ ] If a window has <6 samples or <20 seconds of real measured span, PNG export must be disabled.

## A/B selected PNG
- [ ] Compare two runs; select 00:15–01:20 or another shared interval, then export TXT and PNG; all numbers and B−A signs must match.
- [ ] Toggle GPU>=15% chart-only filter and full unmatched timeline. Neither can change PNG statistics.
- [ ] Compare interrupted run or different GPU load. PNG must visibly include LIMITED COMPARABILITY and a real warning, not a winner verdict.
- [ ] Change selected range; each PNG chart must use its actual elapsed start/end (no reset to zero after cropping).

## Sharing and safety
- [ ] Save only: opens Android document picker, saves image, does NOT invoke share sheet.
- [ ] Save & share: after saving, Android share sheet opens, recipient can read exactly the selected image. No network request inside CoreMetrics.
- [ ] Cancel picker and deny document-provider write: no crash, no share, original sessions unchanged and readable error.
- [ ] Try Frosted, True Glass, Modern Colorful and Technical Cockpit. Export visuals differ while numbers stay identical for same source CSV.
- [ ] Test repeated exports with Eden active. No persistent bitmap cache/memory leak, no application ANR and no HUD loss due to export.
- [ ] Image must include provenance, gaps/power caveats and no fabricated FPS, performance winner or thermal-throttling claim.

## CI release gate
- [ ] GitHub Actions for exact final commit: testDebugUnitTest, assembleDebug, assembleV090base, assembleV090themes all successful.
- [ ] Check downloadable Frosted and Themes artifact ZIPs reference that final SHA.
- [ ] RC1 readiness is a separate step; see RC1_READINESS_PLAN.md.