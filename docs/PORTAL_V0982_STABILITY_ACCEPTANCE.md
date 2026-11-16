# CoreMetrics v0.9.8.2 — Stabilization and comparison correctness

**Build:** v0.9.8.2-stability-analysis-alpha / versionCode 41. Derived from
v0.9.8.1 without changing collector cadence, Quick Settings tile actions,
foreground lease recovery semantics, overlay touch ownership or BAT T colors.

## Changes
1. SQLite **schema v8** adds a single `recording_settings` row:
   label, configuration, sample interval, HUD enabled, active test-profile ID,
   serialized HUD options. Initial v7→v8 migration copies previous values
   from app preferences ONCE, then SQLite is authoritative across Android
   processes. Historical sessions, samples, links and test profiles unchanged.
2. Both QS tiles and RecordingService ARM/START read the same SQL defaults.
   Commands with explicit Intent extras still override the saved defaults.
   No silent defaulting to a stale local SharedPreferences cache.
3. Selecting an active Quick Test Profile updates the SQLite settings row in
   one transaction. Starting REC snapshots that selection within the existing
   session-creation transaction. Deleting an active shortcut clears its
   selection atomically; previously recorded labels never change.
4. The editor now copies all form fields on the tap that initiates Save, and
   never reads Compose state inside Dispatchers.IO. Delete resets editor state
   only after the database confirms deletion.
5. UI updates SQL recording defaults through one ordered, conflated writer.
   ARM explicitly awaits the database save BEFORE starting standby/closing UI.
   HUD option changes are committed to a shared small JSON snapshot.
6. Compare Studio removes the mathematically invalid comparison of
   per-session "higher GPU activity" percentages, which each use different
   thresholds. A/B now warns only when the ABSOLUTE middle-half GPU-load
   ranges on a common 0–100% scale do not overlap. No claim that matching
   ranges prove the same gameplay workload.
7. Busiest system CPU core shifts require at least five of six valid samples
   at >=35% busiest-core activity, reducing idle-core identity noise. Still
   a PATTERN, not proof of game-thread migration.
8. If the loaded 12k-sample analysis window is incomplete, a conspicuous
   warning now appears above the summary. Segment exports remain disabled.
   Uncapped full-session CSV export remains available.

## Developer acceptance
- Existing JVM tests pass and both DEBUG and R8 optimized-test APKs assemble.
- Android instrumentation tests (assembled by CI) must execute on an Android
  device before claiming real SQLite v7→v8 and HUD persistence validation.
- Verify against a copy of a v0.9.8.1 database: all old records, A/B links,
  Quick Test Profiles and annotations are retained on upgrade.
- UI writes custom label, configuration and 2s interval; QS tile then starts
  REC and the new session has the saved values. Repeat with :monitor already
  running and with :monitor restarted. No stale metadata.
- Select Mr.Purple T30 for next REC; start through QS after Eden is open,
  save; change preset name to T32; verify old session still says T30.
- Delete selected shortcut: next REC must not attach a deleted driver label,
  but earlier recordings retain the original snapshot.
- Save a profile then quickly edit another before the IO job completes:
  exactly the tapped values must persist. A failed delete must not clear UI.
- Enable BAT T then switch HUD metric options while game is running;
  verify QS ARM after process restart sees the same options.
- A/B with low absolute GPU load 20–40% vs high 65–85%: warning on
  nonoverlapping distributions, even if within-session relative high/low
  shares are similar. Same distributions must not trigger that warning.
- Replay known four NFS test cases; cap and core-shift events cannot claim
  frame-rate changes, thermal throttling or driver causality.

## Odin lifecycle and performance (still require device testing)
- REC/STOP and standby ARM/DISARM under game launch, rapid QS toggles,
  overlay permission changes and normal app recreation.
- Single HUD owner, unlock handle accessible, no orphan overlays.
- For simulated process death, active REC may resume only an existing
  valid lease; explicit STOP never resumes sampling; incomplete tick gaps
  remain visible.
- Baseline at both 2s and 5s: recorder P95 collector/DB/HUD/total timing
  comparable with v0.9.8.1; additional SQLite activity happens on user
  settings changes, NOT every telemetry sample.
- SQLite read/write errors must not silently label a recording using stale
  settings; check diagnostic journal and Quick Settings errors.

## Deferred to v0.9.8.3
- Paginated/chunked CSV import and long-session analysis beyond 12,000 rows
- Battery current/voltage validation, cumulative counters, device profiles
- A/A repeatability analysis and grouped multi-run comparison
- Complete backup and restore, typed status provider, large UI refactor,
  privileged FPS or new HUD appearances
