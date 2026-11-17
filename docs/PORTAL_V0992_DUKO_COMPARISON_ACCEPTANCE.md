# CoreMetrics v0.9.9.2 — DUKO Comparison Report (development alpha)

VersionCode **45**, SQLite **v9** unchanged. Based on v0.9.9.1
DUKO Session Insight branch, **not yet hardware accepted**.
No new permission, network usage, recorder scheduling, root dependency,
Shizuku, HUD controls, process or continuous telemetry loops.

## Scope / user flow

- **Compare → DUKO Comparison Report → select 2–5 saved sessions** (newest 50 shown).
- Select one **baseline** from the chosen sessions. Baseline means only
  the denominator/reference for signed differences; not "best" performance.
- Optionally declare a single test variable: graphics driver, performance
  mode, fan, ClusterTune or undeclared. Labels are manual metadata only.
- Choose **DUKO Comparison PNG / DUKO Comparison PDF**, then a destination
  in Android's Storage Access Framework. Analysis and rendering run on IO.
- No baseline selection, session labels or manual test settings are written
  back to source recordings.
- The original A/B, A/A repeatability, old scorecard, all single-session
  v0.9.9.1 exports, 2s/5s REC, CM REC/CM HUD and four appearance themes
  are retained.

## Report output

- PNG: one **1600×2200** white/print-style matrix/summary.
- PDF: native Android vector A4 pages for the same metric matrix,
  original-device/manual-test metadata, all methodology notes, and every
  quality/provenance notice in paginated sections. No external rendering
  dependency, screenshot HTML, network or unbounded bitmap pages.
- Uses the **same DukoComparisonInsight.Result** for both formats.
- Nine sensor metrics: CPU/GPU/BAT T, system CPU/GPU/busiest-core
  utilization, battery discharge W, GPU MHz, memory PSI.
- Each run shows original full-run P50/P95 and sample coverage.
  Signed ΔP50 means "run minus selected baseline" in actual metric units.
  Ineligible deltas print unavailable rather than 0 or a fictional percentage.
- Discharge Wh is not ranked between unequal-duration recordings.
  Full-session statistics cannot synchronize gameplay scenes.

## Data integrity

- Reuse DukoReportLoader + DukoSessionInsight, full chronological stored data,
  capped at **250,000 per session**. No partial 12k preview and no accepted
  incomplete dataset. For five chosen recordings, analyze each saved report
  sequentially off-main.
- All source recordings require six or more points spanning 20 seconds.
  Count, source bounds, timestamps and PDF data remain immutable.
- Display raw sensor percentiles even when coverage is poor, but suppress
  numeric baseline deltas when either reading is absent, signal sample
  coverage is under 80%, or original device identity differs/is unknown.
- GPU load/frequency deltas require identical known, validated GPU telemetry
  provenance for baseline and run. A readable but **not calibrated**
  KGSL/Mali path is insufficient; preserve raw values, warn, suppress delta.
- Battery-terminal W deltas additionally require 80%+ valid discharging-time
  coverage and matching non-missing source provenance. Flag unsynchronized
  current/voltage ages; do not claim chip power or game efficiency.
- Warn on device identity, recording completion, gaps, cadence ratio >=1.5,
  duration ratio >1.25, game labels, changing metadata other than declared
  variable, or missing/unknown user-declared test settings.
- Confidence labels: **INSUFFICIENT COMPARABILITY**, **LIMITED COMPARABILITY**
  or **OBSERVATIONAL ONLY**. They are not statistical p-values and do not
  establish matched scenes, repeatability, a frame-rate winner or causal
  thermal throttling. No automated device performance ranking.

## Required automated gates

1. JVM: 2/5 distinct sessions, baseline selection, signed deltas and
   preservation of the user's chosen session order.
2. JVM: unknown/different original hardware suppresses between-run deltas.
3. JVM: missing/unvalidated GPU paths suppress GPU load/clock deltas,
   missing battery provenance suppresses W deltas, while unrelated valid
   metrics may still show observational differences.
4. JVM: below-80% sample or discharge coverage suppresses ineligible
   fields. Declared graphics-driver test does not excuse differences in
   render scale, fan or CPU settings.
5. JVM: the immutable SAF request serializes IDs/baseline/declared variable
   without including free-text or device-sensor readings.
6. Android instrumentation (execute, not merely compile): import two complete
   recordings into SQLite, run full loader, render/decode 1600×2200 PNG,
   render/open every native PDF page and confirm source row counts unchanged.
7. Existing v0.9.9.1 and older tests and debug/release/R8 builds remain green.

## User Odin 2 Portal acceptance (deferred at user's request)

- Back up important sessions before a build signed with a different
  Android debug certificate; debug/optimized artifacts cannot coexist.
- Compare 2, 3, 5 sessions. Reorder selection, swap baseline, change declared
  variable and verify the correct signed ΔP50 and report header.
- Compare Hades T30 vs StevenMXZ runs as observations, **not** FPS winners.
  Validate at least one comparable and one intentionally incompatible
  recording source, one low-coverage run and one dataset over 12k samples.
- Open every PNG/PDF on the device. Inspect label clipping, page breaks,
  units, warnings, all provenance data and legibility at A4 print size.
- Test SAF cancellation, rotation, unavailable destination, malformed CSV,
  repeated export, source deletion, and no zombie recording after export.
- Confirm REC/STOP, standby HUD, tiles, telemetry, battery-temperature
  advisory, old single-session DUKO outputs, legacy reports and ZIP backup.
- Measure export time and peak memory on an 8GB Odin 2 Portal for a
  multi-session stress case. The 250k×5 allowance is a *limit*,
  not a performance claim.

Keep PR in **draft**, and do not merge v0.9.9.1/v0.9.9.2 into main
until the user executes acceptance tests. This version is an alpha,
not a public-release candidate.

## v0.9.9.3 ideas (out of scope here)

- Report visual refinement following hardware feedback, missing-data
  semantics, and limited comparison refinements where test results justify
  them; no inferred FPS or root-only controls.
