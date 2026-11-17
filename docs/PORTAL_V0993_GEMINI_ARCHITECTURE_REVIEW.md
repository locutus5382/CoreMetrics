# CoreMetrics v0.9.9.3 — independent review of Gemini's F1/F7/F10/F11 suggestions

Reviewed on 2026-11-17 against the actual v0.9.9.3 repository. Do not
implement suggested snippets without checking existing components, public
Android APIs, schema migrations and measurement provenance.

## F1: Cross-process intent / SharedPreferences

**Already addressed by existing production code; do not replace it with
Gemini's `active_game` / `active_driver` / `active_fan` preference keys.**

- `SessionStore.recordingDefaults()` selects the current user settings from
  the SQLite `recording_settings` row. `SessionTile` reads this row freshly
  and passes label/configuration/interval in the START intent.
- `RecordingService` accepts those explicit extras and otherwise uses a new
  SQLite read, not the other process's `SharedPreferences` cache.
- `SessionStore.begin()` captures the selected test profile and its driver,
  scale, fan and tuning tags in the same transaction that inserts the session.
- `SessionStoreOperationsTest.crossProcessSqliteSettingsAndProfileSnapshotSurvivePresetRename`
  already verifies two independent SQLiteOpenHelper instances and the
  immutable profile snapshot. Retain this Android integration test.
- There is a narrow distinction between the tile click instant and the
  session creation instant. A profile can change in that interval; the
  current contract binds metadata to the committed session creation.
  Freezing the profile at click time would be a different feature/contract,
  not a stale-cache bug.

## F11: Session-list aggregate counts

The history query currently uses indexed correlated `COUNT(*)` and two
`EXISTS` predicates. Relevant pre-existing indices:
`samples_by_time(session_id,time_ms)` and
`samples_by_session_power(session_id,battery_status)`.

A local **synthetic, in-memory desktop SQLite** microbenchmark used 80
sessions with 3,000 samples each (240,000 rows total), a warm connection
and five iterations per query:

- Current three indexed correlated expressions: approximately **7 ms median**
- CTE recent sessions plus grouped `COUNT / MAX(CASE WHEN status LIKE ...)`:
  approximately **57 ms median**

The grouped query was semantically equivalent on that dataset but around
8 times slower. This is **not a phone benchmark**; it only demonstrates that
reducing the number of SQL subqueries is not necessarily a win. F11 is a
hypothesis, not a verified bottleneck.

**No SQLite v9→v10 migration** in v0.9.9.3. Denormalized counts would have
to be backfilled and updated atomically on every append, import, recovery,
archive restore, partial deletion and interrupted session. Wrong cached counts
are worse than a slow history screen. Instrument Android query latency and
`EXPLAIN QUERY PLAN` with representative 12k–250k rows before changing schema.

## F7: Dynamic hardware / energy

Already present:
- `DeviceCapabilityProbe`: strictly on-demand and bounded sysfs/procfs
  capability and battery candidate checks.
- `CpuPolicyTelemetry.names` / `SystemCapabilityParser.cpuPolicyNames`:
  numeric-sorted dynamic cpufreq policy discovery with strict bounds.
- `GpuTelemetryProbe`: read-only KGSL/Mali/generic-devfreq fallback,
  provenance and 30s retry throttling.
- `BatteryDesignCapacity`: design-energy candidate from energy_full_design,
  or from charge_full_design **only when a nominal voltage source exists**.

Fixed a misleading source label: the nominal voltage field was previously
described as `VOLTAGE_MIN_DESIGN` in the returned provenance string.
New battery design-energy tests verify micro-unit conversions, missing
nominal voltage, and unverified status.

**Do not use** reflection on `com.android.internal.os.PowerProfile`,
a non-SDK interface subject to Android hidden-API restrictions, or multiply
arbitrary mAh by fixed 3.85V. A handheld's pack may contain multiple cells;
design energy is not current usable energy. No runtime estimate without
validated pack energy and source age.

## F10: Compose recomposition

The Activity is large, but Compose does **not necessarily redraw every
visible component whenever any state value changes**. Reads trigger scoped
invalidation; skipped/restarted groups depend on Compose state and compiler
stability. Gemini's proposed example still reads `collectAsState()` at
the parent level, so it does not automatically isolate all updates.

Already present:
- Lifecycle-bound status loop with slower polling outside Live/HUD
  (1.8s on Live/HUD, 4.5s in other active pages, 8s when not monitoring).
- Cached selected session sample lists and clearing of comparison/detail
  caches when leaving their screens.
- `DukoComparisonPanel` LazyRow for up to 50 candidate session chips.

Next requirement: profile Compose recomposition counts and frame duration
**on the Odin** during REC/HUD and during Compare tab interaction. Extract
the changing status/gauge composition into child components only where the
profiles show actual work. Avoid adding another unmeasured architecture
or creating new status IPC calls merely for a UI refactor.

## DUKO model and evidence semantics

`DukoSessionInsight.Report` and `DukoComparisonInsight.Result` already
separate full persisted analytics from bitmap/vector rendering. Sensor
statistics and bounded chart points live in the model, not in the renderer.
Evidence is explicitly OBSERVED, PATTERN or USER_DECLARED; calculated P50
and P95 are typed numeric statistics with source coverage.

Gemini's alternate DukoReportModel is a possible design sketch, NOT a
drop-in replacement. `List<Float?>` for every timestamp and every sensor
of a 250k×5 comparison could duplicate large time series and create
substantial boxed-object RAM pressure, whereas our model retains fixed
280-bucket charts and primitive quantile arrays. Unsupported thread
migration should not be labelled `INFERRED` as though it were measured.

## Acceptance

- No changes to recorder service, sampling loop, HUD life cycle, SQLite
  version, permissions, report queue order or report generation triggers.
- Battery provenance/unit regression tests must pass.
- All v0.9.9.3 JVM/build, migration, archive, PDF and API 33 instrumentation
  tests must pass. Odin verification is necessary for actual PSS and frame
  performance claims.
