# CoreMetrics v0.9.9.5 — HUD Layout Studio and Parallel Stability Track

Status: **draft alpha**, pending CI and Odin 2 Portal acceptance.
Based on v0.9.9.4 draft PR #42. VersionCode **48**; SQLite **v9**
unchanged. Previous v0.9.9.3 DUKO FIFO and report analysis remain intact.

## Three selectable HUD presentations

1. **Classic pill (default)**. Existing CoreMetrics pill, unchanged as the
   default preference, with familiar two-line metrics and right controls.
2. **Compact Glass Card**. A stacked glass panel with CoreMetrics cyan title
   and hairline border, two-column telemetry text and a bottom REC/dock/lock
   control row. No real-time blur shaders, expensive blur backdrops or
   background image allocations. The historical "pro" rounded-card choice
   migrates to this option; unknown layout IDs recover to the original pill.
3. **Horizontal Telemetry Ribbon**. Low-profile dark blue glass strip with
   compact monospace readouts and right-side controls. On smaller viewports
   it uses multiple rows rather than deliberately dropping requested metrics.

All share *one and only one* WindowManager root, the same REC/STOP, lock /
independent unlock chip, dock, close and OS-warning indicator. No additional
overlays, services, telemetry listeners or extra sensor polling. Standby does
not sample. HUD option snapshots remain cross-process SQLite-backed. Only
presentation changes; stored sample data and warning semantics do not change.

The HUD Studio includes a selection preview using **fixed illustrative
values**, not actual sensor measurements. Opacity, size, visible metrics,
adaptive warning colors, battery temperature option and recording dot color
apply in all three modes.

## Required automated checks

- JVM: unknown/empty layout -> classic pill, legacy "pro" -> card, formatting
  includes all selected metrics, narrow-ribbon wraps without truncation,
  font size still increases Small → Medium → Large.
- Android instrumentation: layout persists through SQL snapshot and Intent,
  and the optional battery-temperature setting survives format switching.
- Existing recording, ZIP archive, data migration, DUKO PDF, cancellation,
  Quick Settings, thermal and metric-eligibility tests remain unchanged.
- JVM unit + AndroidTest compile + debug + R8-minified test build must pass.
- API 33 emulator validation must execute: compilation alone is not proof
  of runtime overlay correctness. Known GitHub Actions emulator/ADB instability
  in v0.9.9.4 may require independent device validation.

## First Odin visual + stability test

1. Preserve session exports/backups, then install a compatible debug APK.
   GitHub Actions debug keys can differ across runs and require uninstall.
2. HUD Studio → **Classic pill**. In READY, verify no telemetry reads.
   Open Eden/Hades → REC. Check 2s or 5s recording, HUD presence and STOP.
3. Switch to **Compact Glass Card** while recording. Confirm one window,
   heading, legible two-column readings, bottom REC/STOP/dock/lock controls,
   correct metric values, and unchanged session ID.
4. Switch to **Horizontal Telemetry Ribbon**. Check all selected readings,
   horizontal fit, landscape orientation, no clipped controls. Turn all
   available metrics on and test Small/Medium/Large and opacity 30%/90%.
5. Lock each layout. Confirm pass-through, independently touchable unlock
   chip, visible battery-temp outline and OS warnings, and no stuck overlays.
   Unlock, dock to screen edge, expand and drag. Check window geometry and
   ability to start/stop REC with the HUD alone.
6. Hide HUD using CM HUD Quick Settings while REC continues; show again,
   verify no double overlays or duplicate active session. Verify CM REC tile
   state and stop, then leave Eden and open Sessions.
7. Relaunch CoreMetrics and confirm selected layout persisted. Verify
   switching layout did not alter stored telemetry, sessions, battery source
   metadata or DUKO outputs.
8. Settings → Recorder diagnostics (explicit request only). Preserve process
   exit/log output and screenshot of each layout, both READY and REC.
   Compare sampled PSS and CPU overhead against the previous stable pill
   version if hardware measurements are available.

## Concurrent stability and performance work

- CI: repair emulator host/ADB issues, preserve actionable logcats and rerun
  isolated SQLite migration/restore and PDF instrumentation suites.
- Recorder: harden only reproduced Odin lifecycle, overlay persistence,
  recovery lease, Quick Settings state and user STOP/REC issues.
- Memory: profile 2–5-session DUKO reports. Keep report generation strictly
  user-requested, in FIFO order, with one active export, progress + Cancel.
- Reporting polish: clarify sensor eligibility, cancellation/failed SAF
  cleanup, report readability and errors without assuming measured FPS,
  hardware driver superiority or validated GPU/battery provenance.

Release gate: keep PR #42 and the HUD-layout PR draft until both successful
build evidence and Odin gameplay acceptance. Do not start renaming CoreMetrics
or migrating the repository during development.
