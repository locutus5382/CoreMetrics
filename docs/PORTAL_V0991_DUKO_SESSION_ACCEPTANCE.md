# CoreMetrics v0.9.9.1 — DUKO Session Insight (development alpha)

VersionCode **44**, SQLite schema **v9** (unchanged). Base:
v0.9.9.0 Device & Measurement Foundation. Existing compact HUD,
Quick Settings, 2s/5s recorder cadence, v0.9.8.x A/B and A/A panels remain.

## Deliverable

**Sessions → Analyze → DUKO SESSION INSIGHT → DUKO PNG / DUKO PDF**.
The user chooses a document with Android's Storage Access Framework,
then all analysis/rendering happens on Dispatchers.IO. On a failed write,
CoreMetrics attempts to delete only the incomplete selected document.

- PNG: one **1600×2200** light/print-style summary page (~13.4 MB
  uncompressed ARGB before PNG encoding; file size depends on image).
- PDF: native Android `PdfDocument`, vector text and charts, portrait A4,
  one summary page, one interpretation/glossary/provenance page and, if
  applicable, further paginated event pages (up to 32 stored event entries).
  PDF has no embedded bitmap screenshots or third-party fonts.
- Both forms use **one frozen DUKO Session Insight report model**, and
  therefore the same underlying statistics, event evidence and caveats.
  Reports are completely offline.
- Existing CSV, TXT, cropped PNG scorecards, Session Analysis and old
  Quick Test Profiles are preserved; this is a new optional export.

## Analytics

- Complete chronological cursor scan independent of **12,000-row UI
  preview**. Maximum 250,000 rows is explicit; larger recordings fail with
  an explanation rather than exporting incomplete results.
- Exact sample P50/P95/peak for CPU/GPU/battery temperature, system CPU/GPU
  utilization, busiest core, eligible battery-terminal W, GPU frequency,
  and memory PSI. No FPS or inferred game frame timing.
- Time-weighted integration of **valid adjacent discharging W samples**.
  Correct units: W × milliseconds / 3,600,000 = Wh. No bridging
  large gaps, charging or absent readings. A separate coverage percentage
  makes unsupported energy obvious.
- Battery temperature exposure: measured valid time at >=39 °C and
  >42 °C. HOT advisory matches the app-defined >=15s continuous rule,
  but is **not** a manufacturer critical limit.
- Events: recorded gaps, detected frequency-cap transitions (not their
  cause), Android thermal policy, system memory pressure, sustained
  >=95% GPU or system busiest-core utilization over ~120 seconds,
  and BAT T app advisory. Observed and pattern evidence labelled.
- Up to 32 event descriptions on PDF supplementary pages, with any
  suppressed count explicitly shown. Chart reducer uses fixed
  280 temporal buckets with extrema, not unbounded line vertices.
  Temperature and GPU load are plotted on **separate axes**; gap segments
  are never deliberately interpolated.
- Caveats: device/ROM GPU path unvalidated, missing original device ID,
  unknown voltage-current update ages, battery-terminal not chip power,
  manually declared test settings not confirmed, no per-game FPS,
  throttling causality or driver ranking.

## Required tests

1. JVM: exact P50/P95 and Wh numerical check with fixed 4 W and
   250-second source duration; no 1000× unit error.
2. JVM: missing/charging/disconnected sources never become 0 W or
   fabricated energy, and recording gaps have no connecting discharge.
3. JVM: >12,000 valid synthetic readings retain a late peak and bounded
   graph output (<=560 points per plotted metric).
4. JVM: BAT T >42 °C continuously 15s triggers a labelled
   app-defined advisory; no statement of CPU/GPU critical thresholds.
5. Instrumented Android: actual SQLite v9 CSV import of 12,006 samples,
   full cursor processing, PNG decode 1600×2200, native PDF header,
   and original stored count intact.
6. Manual Odin: 2s/5s recording, firmware GPU source, BAT T, session
   restore and device metadata accuracy; old PNG/TXT/CSV not altered.
7. Manual PDF review: render pages and inspect typography, clipping,
   event truncation, axis labels, battery source and glossary on Android.
8. Manual long-session test: measure export time and heap usage after
   finishing REC; no new background polling or resource growth during REC.
9. UI cancellation/rotation while the SAF picker is open does not
   render a partial report or leave zombie recording services.
10. Reports must not imply that 5s samples provide frame smoothness,
   that lower watts prove greater gaming efficiency, or that OS thermal
   NONE proves there was no throttling.

## Limits / next milestone

- v0.9.9.1 is **single recording only**; v0.9.9.2 will introduce DUKO
  Comparison Report across two to five sessions with common-field
  matrices, selected baseline and confidence-aware conditions.
- GUI still has a 12k preview cap. The NEW DUKO report alone scans
  all 250k allowed stored points. Large PNGs are not generated:
  one page only, with extra details in the PDF.
- Genuine Mali sensor semantics, vendor-specific throttling thresholds,
  confirmed per-game FPS and portable battery usable capacity require
  independent device validation; none are invented in this version.
- GitHub CI compiles Android instrumentation but does **not** execute
  those Android UI/integration tests. Confirm actual device acceptance
  before merging this alpha to the main branch.
