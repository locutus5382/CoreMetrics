# CoreMetrics v0.9.9.7 — Claude review: stability and memory acceptance

Build: 0.9.9.7-stability-memory-alpha (versionCode 50).
Baseline: visual-polish PR #45, retaining all five HUD styles unchanged.

## Changes implemented after independent v0.9.9.4 review

1. Sampling failures are isolated within the recording tick. A dropped tick
   is journaled as SAMPLE_DROPPED and is never backfilled with fabricated data.
   One retry for an SQLiteDatabaseLockedException is bounded by a 150 ms delay
   on Dispatchers.IO. Five consecutive dropped ticks or a permanent full/corrupt/
   cannot-open SQLite failure still terminates the recording. Resumption
   journals SAMPLE_RECOVERED; saved sample counts advance ONLY after db.add.
2. DUKO user-requested FIFO exports now query Android lowMemory before analysis.
   Explicit OutOfMemoryError cleanup attempts to delete the partially written
   SAF document; both success and failure are user-visible. SAF cleanup uses
   the application ContentResolver, not the Activity context.
3. Compare-tab BenchmarkAnalyzer.analyze moves out of Compose composition
   to a cancellable Dispatchers.Default produceState keyed on the two selected
   recordings, their points and warm-up setting. Unrelated list refreshes
   should no longer synchronously re-analyze.
4. SessionDiagnosticsAnalyzer uses a growing *primitive* sliding median
   window instead of per-point Pair allocation, map and sort. It uses a single
   sorted DoubleArray per metric for median and P95 rather than duplicated
   boxed-list sorts. All gap resets, time spans and null/readiness semantics
   must remain identical to prior output.
5. KGSL busy-ratio parsing reuses a compiled whitespace regex rather than
   constructing it every sampling tick.
6. Version/provenance bumps do not change SQLite schema (v9) or DUKO report
   format (0.9.9).

## Not adopted blindly from the review

- Foreground-before-I/O: desirable but requires explicit recovery/permission
  state-machine validation to avoid displaying a misleading active notification,
  or overriding a valid persisted same-session lease.
- Global store singleton / async settings writes: no unsafe unsynchronized
  SharedPreferences replacement; SQLite remains the source of truth and
  explicit Intent extras retain precedence.
- Removing @Synchronized from cursor scans: requires writer-vs-reader
  concurrency tests, deletion during export, and snapshot-consistency checks.
- UI cache trim on onStop: requires reconciling SAF pickers and ensuring
  Sessions/Compare data is fetched again on foreground return.
- Global catch-all UI database fallback: must surface actionable error state
  rather than silently replace data with empty lists.
- Attribution throttle: changes game-package provenance during app switches;
  measure real Binder overhead before changing the current per-tick policy.
- Broad Gradle/Kotlin upgrade and service state-machine refactor: separate
  projects with longer test windows. Real-time blur/HUD renderers untouched.

## Required validation

- JVM suite (new dropped-tick policy and 240-value sliding median reference)
  + APK debug and R8 optimizedTest compile and pass.
- API 33 database-integrity + reports-ui instrumentation both green.
- Odin: ten deliberate transient sample failures in a DEBUG fault-injection
  build: 1–4 consecutive errors keep session open, five stop with FAILED,
  sample gaps are reported; actual exported rows never fabricated.
- 60-minute Odin gameplay recording: journal's SAMPLE_DROPPED/SAMPLE_RECOVERED
  and final session state reconcile, no extra service/overlay instances.
- Five-session DUKO comparison with Eden open: simulate Android lowMemory,
  cancel and partial destination cleanup; preserve one-at-a-time FIFO ordering.
- Benchmark Compare scrolling and panel response with two 12k-sample sessions;
  inspect gfxinfo frame time before and after and ensure new content appears.
- Diagnostics CPU time, GC counts, PSS and exact rolling-median results on
  12k points; report source validity and time gaps unchanged.
- Do not assert RAM savings without measured Debug.getPss / dumpsys meminfo,
  using optimizedTest build and separate UI/monitor processes.

Do not merge until these acceptance gates and user-approved HUD design pass.
