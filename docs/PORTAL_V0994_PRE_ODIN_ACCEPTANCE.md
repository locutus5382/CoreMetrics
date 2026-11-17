# CoreMetrics v0.9.9.4 — Pre-Odin Hardening and Validation

Base: v0.9.9.3 draft PR #41. **VersionCode 47**.
SQLite **version 9 unchanged**. This is an Android alpha, NOT a publicly
validated or hardware-accepted release.

## Purpose

Prepare a reliable and reproducible first Odin 2 Portal acceptance test
without introducing speculative device fixes, privileges, telemetry work,
automatic report preparation, or new HUD themes.

## Changes in this milestone

### User-initiated, read-only diagnostics

- Settings → Recorder diagnostics → View process-exit & lifecycle log now
  includes a bounded **recent recording-integrity snapshot**. The snapshot
  performs SQLite indexed COUNT/MIN/MAX checks for up to five saved sessions
  but never materializes raw sample arrays. It flags missing saved samples,
  counts changing across two diagnostic queries, impossible timestamps,
  and saved finish dates earlier than start.
- The snapshot shows numeric session IDs and statuses only, not game names,
  paths or package labels. The collection occurs ONLY when the user presses
  the existing diagnostics button. No background collection or report starts.
- The same manual report now includes this **UI process's** instantaneous
  sampled PSS and allocated-used Java heap, labeled not peak memory.
  The recorder is a separate :monitor process; UI PSS is NOT recorder PSS.
- "No inconsistency detected" only validates basic count/time structure.
  It does not prove full telemetry continuity, reliable overlay ownership,
  hardware power accuracy, stable game FPS or absence of process kills.

### CI and release provenance

- API 33 emulator test execution is bounded to 20 min within the existing
  40-min job; a stall results in a failed job instead of silently hanging.
  XML/HTML reports remain uploaded on completion or failure when generated.
- Build and test APK artifacts clearly identify v0.9.9.4. The first Odin
  install should use the debug artifact, never assume a debug certificate
  from earlier GitHub Actions builds is install-compatible.
- No new permission, root/Shizuku dependency, SQLite schema change,
  recorder or HUD service change, timer, automatic report, or extra sensor
  polling. FIFO DUKO report requests and cancellation remain as v0.9.9.3.

## Automated acceptance

1. All JVM tests, AndroidTest Kotlin, Debug and R8-optimized builds succeed.
2. SessionIntegritySnapshot JVM tests cover clean/empty/active/invalid times,
   changing row counts and privacy boundaries.
3. Android instrumentation imports a completed session into an isolated
   SQLite DB and checks the manual integrity report without changing rows.
4. Full API 33 instrumentation executes and reports all migration, archive,
   PNG, PDF, queue and comparison tests; no silent green from compilation.
5. No performance/RAM claims without comparable on-device PSS measurements.

## Initial Odin 2 Portal test checklist

1. Back up saved sessions before installing the v0.9.9.4 debug APK.
   The debug-signing certificate may be different across CI runs.
2. Start HUD standby, launch Eden/Hades, activate REC from HUD and/or
   Quick Settings, record 5–10 minutes. Leave game, stop, verify one
   saved session and no duplicate HUD overlay.
3. Repeat with HUD hidden during REC, with REC tile STOP, 2s and 5s
   sample intervals; inspect session identity and sensor provenance.
4. From Settings → Recorder diagnostics, request the report AFTER the
   gameplay test. Copy the output, including lifecycle journal, Android
   process-exit reasons, capability readouts and recent saved-session
   integrity summary. This report is not a live continuous monitor.
5. Export DUKO Session PNG/PDF and compare 2–5 sessions with baseline
   changes, unavailable sensors and manual driver/profile tags.
6. Verify FIRST-IN-FIRST-OUT manual report queue with two or more
   user requests; only one active analysis and one document picker.
   Cancel one request and verify the next continues; cancel SAF and
   confirm any incomplete document is handled correctly.
7. Inspect all PDF pages and compare exact values against source CSV.
   For large comparisons, note elapsed export time and sampled UI PSS
   (sampled values are not true peaks).
8. Confirm legacy CSV/TXT/A/B, backups/restores, readable reporting,
   correct Android battery state, no lost data or interrupted recorder.

## What remains out of scope until Odin data arrives

- Vendor GPU path calibration; thermal source correctness and thresholds.
- Hardware-specific HUD overlay persistence and Android process recovery.
- Verified in-game FPS, driver ranking, CPU/GPU bottleneck causation.
- New Compact Glass Card / Horizontal Telemetry Ribbon layouts.
- v1.0 branding, Play Store publication, or repository migration.

Keep draft PRs #39, #40, #41 and this milestone draft/unmerged until
device results establish safety and stability.
