# CoreMetrics v0.9.8 — Analysis Intelligence acceptance

**Development candidate.** v0.9.8 adds post-session analysis and manually
declared test conditions. It does not modify the active recorder's sampling
interval, overlay, Android permissions, thermal sensor collection, QS tiles,
or REC/STOP recovery logic. SQLite migration: **v6 → v7**, append-only schema
for user metadata and a new reusable test_profiles table. Export valuable
recordings before installing any APK signed with a different certificate.

## Quick Test Profiles (manually entered, never device commands)
- Sessions → Edit → Quick Test Profiles: create a custom named profile (e.g.
  Steven MXZ R2, Mr.Purple T30), optional game/track, graphics driver and
  resolution, Odin performance Standard/Performance/High Performance, fan
  Off/Quiet/Smart/Sport/Custom, ClusterTune Stock/Small/Medium/Large/Custom.
- From Live select a saved preset before tapping REC. After STOP, check that
  the new session has a snapshot of the chosen profile; no changes to actual
  hardware settings are performed.
- From Sessions → Edit, explicitly apply a preset to one completed recording.
  No automatic retroactive tagging or rewriting source sensor samples.
- Edit/rename/delete the saved shortcut. Earlier sessions keep their own
  stored strings; removal of the preset clears next-recording selection.
- Try blank/invalid names, long strings, CSV formula-like drivers and duplicate
  profile names; reject invalid values without overwriting existing metadata.
- Save a new session CSV and reimport it: profile_name, performance_mode,
  fan_mode and cluster_tune_mode should survive; historical pre-v0.9.8 CSVs
  must still import with unknown fields rather than guessed values.

## Activity and important events
- Session analysis shows a sparse significant-event timeline and suggested
  higher/lower *relative GPU activity* periods, with measured timestamps.
  These periods are NOT gameplay/menu/race boundaries.
- Test a real sustained CPU cap reduction (if desired using a separate safe
  tool). Expected: "Observed CPU frequency ceilings changed" with actual
  P3/P7 values. Not "ClusterTune did it" unless a user declared metadata.
- Observe CPU7 → CPU3/4 busiest-system-core pattern shift if present:
  label is a /proc/stat activity pattern, not game-thread migration.
- A two-minute near-95%-GPU/CPU episode should be labeled informational:
  >=95% of VALID samples in a continuous >=120s window at >=95% load,
  >=90% expected sample coverage, no large gaps. This does NOT prove FPS
  drops, throttling, or a bottleneck.
- Positive Android thermal status creates a device-wide OS-pressure event.
  Consecutive >=10% system PSI some avg10 samples create a memory-pressure
  event. Absent support never becomes a fabricated zero or "normal" status.
- Large recording gaps create events and must not be interpolated. Sorting
  is chronological for export; the top 3 event callouts prioritize relevance.

## A/B measurement confidence
- Choose the declared **variable under test** (Graphics driver, Odin
  performance, Fan, ClusterTune, Not declared) in Compare Studio.
- Different driver tags are intentional in a Driver experiment; differences
  in unrelated fan/performance/render scale remain cautions.
- Same values for the supposedly tested variable should generate an
  informative caution, never a benchmark "winner".
- The selected elapsed window is not a synchronized in-game scene; different
  relative GPU activity distribution and initial temperatures are warnings.
- All results remain objective descriptions of battery-terminal watts,
  temperatures, loads and frequency limits. No frame performance claims.
- CSV, TXT, single PNG and A/B PNG exports stay compatible; the report-format
  label changes to v0.9.8 for the expanded CSV metadata.

## SQLite and regression
- Migrate real app database in place from schema 6 to 7; retained samples,
  links, driver notes and previous session metadata must be unchanged.
- Instrumented v4/v5/v6 migrations include synthetic historical rows.
  Android instrumentation tests are not executed just by a green JVM CI build.
- With two existing saved sessions, verify editing profiles never changes
  their source samples and deleting one never affects A/B associations.
- Replay the four saved NFS CSVs with post-import manual driver labels. Avoid
  declaring a driver "faster" on the basis of battery watts alone.
- Validate after Odin HUD v0.9.7.5 testing in ONE combined session: REC,
  STOP, QS, touch lock, corners, LMK standby and active REC recovery.
- Match recorder `RECORDER_TIMING` P95 against 0.9.7.4: the new
  analysis runs post-session, so it should not alter recording overhead.

## Deferred deliberately
- Optional battery-temperature HUD metric and validated vendor thermal
  thresholds: v0.9.8.x if Odin tests justify them.
- DUKO Session Insight and DUKO Comparison Report (PNG/PDF): v0.9.9.
- Five-way on-screen graph, FPS inference and new HUD themes are not included.
