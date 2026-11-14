# CoreMetrics v0.9.1: reconciled assistant + Gemini source audit and rollout

**Source:** v0.9.0 GitHub branch `feature/coremetrics-v090-png-scorecards`, snapshot eea10f2a. This document is source reasoning, not a physical Android test.

## Agreed defects fixed in v0.9.1
1. Recorder service `onDestroy()` previously set `stopping=true`, and cancellation interpreted that as COMPLETED even without user intent. Introduced `explicitUserStop` and `destroying`, with separately tested outcome policy. Explicit STOP/DISARM still complete recording, system teardown yields INTERRUPTED.
2. CPU ties removed valid peak load. `CpuMath.maxCoreLoad` now keeps peak percent even when `uniqueBusiest` cannot select a unique core. Do not backfill old missing rows without evidence.
3. Previously malformed standby newline, unformatted diagnostics TXT floats/nullable fields and PNG `°C` units; repaired.
4. CPU-frequency regimes used key changes only, potentially bridging an LMK gap. New continuity blocks split by cadence-based bounded gap limit, never merged during blip suppression. Regime terminal discharge now uses valid adjacent time-weighted intervals; menu-only filters never produce fake connectivity.
5. CSV import accepted duplicates/out-of-order or implausible finite readings. Timestamps now strictly increase; mandatory numeric checks reject implausible imports atomically. Missing sensors stay missing. Main benchmark normalizes temperature/load fields consistently with diagnostics and only uses selected-window charging status.
6. Selected analysis missing/empty window could silently produce a permanent spinner. Explicit `Loading/Ready/Failed` state preserves meaningful errors and does not swallow coroutine cancellation.
7. A/B compares 2s and 5s sampled distributions without cadence warning. This now adds a comparability caution, not a winner judgment.
8. PNG SAF export held an ephemeral request only in non-saveable Compose state and left broken partial documents after errors. An explicit validated request token now survives configuration recreation; failed files receive best-effort removal with honest provider-error messaging.
9. Analyze entry from bottom of Sessions now scrolls to the analysis header.
10. PNG graph's end timestamp field was confusingly called duration; renamed to `graphEndMs` without altering the correct x-axis math. "Within ±2°C" scorecard wording no longer suggests proven thermal equilibrium.

## Where Gemini's proposed urgency/changes were overstated
- Raw TXT double precision is a **presentation defect**, not a P0 data-loss defect. Fixed but below recording and numeric validity.
- The PNG X-axis computation was already correct; the property name was misleading. Renaming is hygiene, not a broken graph.
- Two Quick Settings tiles are not duplicates: Record can start/stop without an overlay; HUD controls an opt-in standby/visibility workflow. Leave both until user testing determines a simpler arrangement is preferable.
- The leaked RAM claim for the PNG generator is not established by code review; its offscreen Bitmap was already recycled in a finally block. Android-device profiling is still required.
- Removing test flavors and tagging `v1.0.0-rc1` immediately would obscure regression comparisons and suggest verification that has not happened. No tag, signing key, release publication or merge is performed now.

## Next incremental steps
- **v0.9.1:** targeted correctness fixes + JVM regression tests + two isolated test APKs + hands-on Odin test. Stop on any confirmed data-loss/false-result/REC crash.
- **v0.9.2 (planned, not implemented in this branch):** optimize diagnostic Canvas paths (decimate to pixels while retaining raw data for math), binary-search cursors, isolate screen state/repositories without a full rewrite, reduce idle status provider process costs, and add Android instrumentation tests for SAF/FGS lifecycles; benchmark before/after.
- **v1.0.0-rc1 (not created):** choose one canonical package and approved themes, code freeze, release-signed APK/AAB, privacy/store declarations, exact-tag CI and 30–60 minute Odin + secondary-device regression. Workarounds for FPS or new visual features are out of scope.

**Validation limitation:** GitHub unit tests cannot simulate Android LMK, process termination, foreground service restrictions, cross-process SQLite races, or Storage Access Framework provider behavior.
