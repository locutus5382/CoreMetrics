# CoreMetrics v0.9.9.3 — Reliability, report performance, Android CI

Branch: feature/coremetrics-v0993-reliability-reporting, based on v0.9.9.2
draft PR #40. VersionCode **46**. SQLite **v9 unchanged**; no migration,
new permission, background sampling loop, recording-service rearchitecture,
network, root or Shizuku dependency.

## Scope and changes

### P0: Integrity and Android instrumentation
- Fix misleading emulator integration assertions **without weakening actual
  production data constraints**. v7 fixture now includes the samples table
  that every production v7 database had; v8 fixture includes battery_status
  already present in v8. Migrations still must preserve rows, metadata and
  battery power source history; real corruption is not automatically "fixed".
- Archive restore assertions must distinguish pre-existing sessions with
  matching manual profile tags from restored ones; verify both groups and
  protect original samples, links and preset metadata.
- Android SAF CreateDocument action/MIME/title checks are authoritative for
  the chooser contract; CATEGORY_OPENABLE may vary by AndroidX implementation.
- Combined updateMetadata accepts null for clearing a companion field;
  invalid non-null input must still be rejected without partial writes.
- CI must **execute** API 33 instrumentation and upload XML/HTML results
  even on failure. Never declare emulator-green solely because APKs compile.

### P1: Performance/stability
- The DUKO quantile accumulator sorts only the populated range of each
  primitive DoubleArray, instead of cloning the full 250k-sample array for
  every metric. Exact P50/P95/min/max semantics are unchanged. Temporary
  saved allocation: up to 9 × 250,000 × 8 = 18,000,000 bytes per full
  sample-rich report, excluding headroom and GC overhead. This is an
  allocation estimate, **not** a measured speed/RAM improvement.
- DUKO cursor streaming reuses the initial COUNT/MIN/MAX bounds instead of
  running an extra full-index count per recording; visited rows must still
  exactly match the initial count. The old public callback overload remains.
- Single-session and 2–5-run loaders accept optional cancellation checks.
  On Android, their document export jobs check cancellation every 512
  samples and between recordings. Do not leave prolonged CPU/SQLite work
  running after an Activity export is cancelled.
- Only one DUKO export can run concurrently (single-session or comparison).
  Reject overlapping picker results and remove their just-created SAF files.
  DUKO selection chips use LazyRow so only visible items are composed.
- Existing 12k UI preview remains distinct from 250k complete-session
  analytics. Keep read-only export on Dispatchers.IO, not during REC/HUD.

### Reporting and informational boundaries
- Continue using a single immutable report model for PNG/PDF. Mark missing
  GPU/battery sources as unavailable; do not manufacture zeros or assert
  95% load proves FPS bottlenecks. DUKO comparison delta eligibility must
  remain tied to device/source identity and measurement coverage.
- Report visual refinements and bigger optional changes are subordinate to
  P0 CI acceptance. Source code build success does NOT certify A4 layout.

## User-initiated FIFO report export (final hardening pass)

- Reports are NEVER generated on app start, REC, standby HUD, Quick Settings,
  a timer or an unsolicited background monitor. Only an explicit tap on a
  DUKO PNG/PDF button enqueues an immutable request.
- A ViewModel-scoped FIFO processes ONE request at a time, including the
  Android document picker. A request cannot analyze data until its own
  user-chosen SAF destination exists. Later requests stay in click order,
  maximum 12 waiting after the current one.
- Queue status is visible in Sessions and Compare. Users can Cancel the active
  report, Remove a waiting request or back out of the document picker.
  Cancellation removes partially created SAF documents where supported,
  then proceeds to the next request.
- Scan progress updates every 512 samples, including across 2–5 recordings.
  Rotation retains the queue through the Activity ViewModel. The coordinator
  is NOT an Android background service and cannot survive process death.
- The coordinator samples this app process's PSS and Java heap at ~10%
  progress increments and reports elapsed time. Values are approximate
  sampled high-water, not measured true peak RAM or device-wide savings.
  No memory polling occurs outside a user-requested export.
- JVM scheduler tests verify FIFO, one active request, immutable selections,
  cancellation of waiting items and maximum queue depth. SAF callbacks
  validate BOTH report kind and format to reject stale picker results.

## Large-session memory and read optimizations

- One reusable SessionStore.ReportScratch (15 sensor fields) replaces a fresh
  25-field Point object on every DUKO report sample. The accumulator keeps
  one separate reusable previous-row snapshot for energy/temperature
  integration. The old Point-based API remains for legacy analyzers.
- Android regression compares optimized DUKO output with the immutable-Point
  path, verifies scratch-object identity over 1,025 rows, and checks that
  cancellation cannot mutate recorded source samples.
- The 120-second high-utilization detector keeps incremental valid/high
  counters, avoiding repeated per-sample mapNotNull list creation.
- PDF/PNG renderers test cancellation between report pages and before/after
  output writes. Some native PDF/bitmap sections are not preemptible.
- The five-run comparison PDF splits its metric matrices into baseline-plus-two
  pages, adds per-session measurement-readiness explanations for ineligible
  deltas, and limits warning pages to eight items. The compact PNG retains
  its full five-session summary.

## Recorder and Quick Settings

- CM HUD and CM REC delayed tile refresh callbacks are removed on
  TileService stop-listening/destroy, avoiding stale work. No sensor loop,
  timer or service is added.
- Same-session LMK/lease recovery, HUD ownership checks, 2s/5s recording
  and independent REC/HUD tile policies remain untouched.
- Hardware gameplay validation is still required on the Odin 2 Portal to
  confirm HUD persistence, STOP/REC, Quick Settings state, and process
  recovery across Eden/Hades launch and exit. Emulator policy tests do
  NOT establish device-overlay reliability.

## Second-pass memory, CPU and cancellation audit

The source of truth is the report code. These savings are **design estimates**,
not measured Odin 2 Portal performance until the APK runs there.

- **Opaque PNG canvas:** Both DUKO summary exporters now use RGB_565 for
  1600×2200 print/white-background canvases, reducing the bitmap's backing
  allocation from about **14.08 MB to 7.04 MB** (decimal units). PDF is still
  native, full-colour vector A4. RGB_565 slightly reduces colour precision;
  inspect labels and cyan accents visually on the Odin before release.
- **Utilization ring:** Two 120-second GPU/core windows use primitive
  timestamp/value arrays with dynamic ring growth, instead of creating one
  boxed timestamp/value Pair per window and sample. NaN is an internal
  unavailable-value sentinel; existing sample-count/coverage thresholds and
  gap reset behaviour must remain unchanged.
- **Extrema chart buckets:** New Extreme objects are created only if a value
  changes a bucket's min/max. Chart finishing emits the two extremes in time
  order without listOfNotNull/distinctBy/sortedBy allocations per bucket.
- **CPU policy parser cache:** Repeated identical raw policy strings reuse
  their parsed signature while real changes still require three consecutive
  observations. The cache never replaces gap/unknown state reset.
- **Report queue cancellation race:** Export coroutine enters its
  try/finally before onDocumentResult returns, preventing a rapid Cancel from
  stranding a pending request with busy=true forever.
- **Regression tests:** Dense 200-ms imported 720-row util streams force ring
  growth; 20/120-degree chart spikes survive time-bucket compaction; repeated
  and then changed CPU policy signatures generate both observed transitions.

Do not interpret reduced allocations as a measured application PSS reduction,
a battery-life improvement or higher gameplay FPS. Actual export time, sampled
PSS, user-visible colour fidelity and process stability need Odin tests.

## Automated acceptance

1. JVM: all existing analytics, full-session quantile, exact Wh and report
   completeness tests pass; add 12k+ sample quantile regression.
2. Android: all existing SessionStoreOperationsTest v4–v9 migrations pass,
   including v7/v8 fixture truthfulness, preserved samples and metadata.
3. Android: SessionArchiveTest restores without overwrites or double-counted
   existing records, and rolls back malformed ZIP input.
4. Android: ScorecardRuntimeTest checks user-selected SAF action, MIME, title;
   DUKO Session and Comparison PDF/PNG render and decode.
5. CI produces debug and R8-optimized APKs and publishes HTML/XML for every
   failed emulator job to diagnose problems without guessing.
6. Under controlled test conditions verify cancelled export releases cursor
   and leaves original rows unchanged; do not merge on compile success alone.

## Odin 2 Portal (user deferred until after development)

- Back up original recordings before a debug-signing-key change.
- Check CM HUD standby, in-game REC/STOP, Quick Settings state after game
  launch/stop, no doubled overlay, and no lost session when a game starts.
- Record 2s/5s sessions and 12k+ samples, export both DUKO formats, then
  compare 2,3,5 runs on the same track/driver tags.
- Check PDF legibility and long labels; compare output numerics with CSV;
  test cancellation, screen rotation, missing sensor data, backup ZIP restore.
- Collect peak memory/PSS, export elapsed time, battery drain and recorder
  impact before making any quantitative speed/RAM claims.

## Future improvements, not silently promised

- Adaptive memory budget for large 5-run comparisons; per-metric readiness
  notes; stronger PDF pagination and bounded chart cache.
- Better low-level HUD lifecycle recovery, QS synchronization and process
  memory telemetry only if Odin evidence identifies real regressions.
- Optional HUD Compact Glass Card and Horizontal Telemetry Ribbon AFTER
  core telemetry/report stability, preserving CoreMetrics colours.

Keep PR #39, #40 and v0.9.9.3 draft, unmerged until hardware acceptance.
