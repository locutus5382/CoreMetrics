# CoreMetrics v0.9.7.4 — standby, sampler and HUD acceptance

**Development/test build. Do not merge to main or publish before Odin 2 Portal acceptance.**

Build: versionCode 37, `0.9.7.4-stability-efficiency-alpha`.
Same Android package, SQLite v6, 34 CSV columns, permissions, 2s/5s telemetry
sampling, REC/STOP logic, and existing four main-app finishes / HUD layouts.
Existing recordings must survive an update signed by the same certificate.

## 1. Standby recovery — most important
- Start standby explicitly and verify **CM HUD: Ready**. No sensors should be sampled.
- Under Eden memory pressure, induce one :monitor process death (or use a
  controlled adb process kill that is not Force Stop). Confirm that Android MAY
  restart the explicitly armed READY HUD once, subject to OEM restrictions.
- The recovered standby should log `STANDBY_RECOVERED_ONCE` and should NOT
  request additional sticky standby restarts. If Android kills it again, use
  an explicit CM HUD/Launch standby action to arm anew.
- Verify the restored HUD is interactive, correctly locked/unlocked, and does
  not create an unowned/duplicate WindowManager overlay.
- New explicit ARM resets the one-shot standby eligibility.
- A STOP-created standby is never eligible for automatic recovery.

## 2. Active recording and STOP safety — must remain unchanged
- Start REC, induce LMK and verify the original SQLite session ID, sample count,
  HUD visibility preference and lock state are recovered when valid.
- STOP immediately before/during recovery. No recovery may restart sampling
  after the user requested STOP; any pending STOP may only finalize the row.
- Test rapid CM REC / CM HUD tile taps, app background transitions, and HUD
  hide/show. Verify there is one :monitor writer and one owned overlay.
- A system Force Stop is intentionally not bypassed; test LMK separately.

## 3. Diagnostic timing — no extra sample persistence
- After a normal recording, Settings > View process-exit & lifecycle log:
  inspect a single `RECORDER_TIMING` event for the session ID.
- Read P95 elapsed milliseconds for `collector`, `db`, `hud`, `total`
  and `start_late`. A hidden HUD may be reported as `hud=na`.
- These numbers include dispatcher waiting and do not equal CPU time, RAM
  usage, or SoC watts. Each report retains at most 256 recent ticks.
- Compare the same game/scene and settings with v0.9.7.3; no blanket
  overhead improvement is claimed without this test.
- CSV column set and sample_duration_ms semantics are unchanged.

## 4. Optional sensors and GPU clock source validation
- Expect genuine zero memory PSI values to be recorded as **0**, not missing.
- On Odin headroom may stay unavailable (NaN). After 3 failed probes it is
  retried at most every 120 seconds; an available sensor returns to 10 seconds.
- Unavailable PSI is retried every 60 seconds after three failed reads; never
  permanently blacklist it. No readings are fabricated for skipped probes.
- The **on-demand** capability report shows raw `devfreq cur_freq`,
  `kgsl gpuclk`, and `devfreq max_freq` readings, or why each is inaccessible.
  They are raw vendor values and do not validate the 401 MHz measured clock.

## 5. HUD performance and configuration
- Drag HUD rapidly over the screen, then release, collapse, expand and lock it.
  Drag layout updates should be coalesced to at most one per display frame.
- Verify drag stopping and docking always flush the final position, and no
  invisible touch blockers remain behind the collapsed tab.
- Changing size/opacity/metrics rapidly should not stall the UI or overwrite
  the REC state. Cosmetic preferences save asynchronously; verify they remain
  after a normal app restart.
- Confirm recording measurements do not cause redundant WM geometry updates
  unless position/width/height really changed.
- No new HUD themes, persistent animation or screen designs were added.

## 6. Required CI checks
- JVM tests include one-shot standby policy, sensor retry schedule and bounded
  in-memory recorder timing.
- Build debug APK, R8 optimized test APK, instrumentation APK and release APK.
- Inspect Android crash/lifecycle reports and compare the 2s / 5s cadence.
- Preserve v0.9.7.3 as a rollback source baseline. **SQLite is unchanged;**
  an APK signed with a different certificate still requires session export
  before any uninstall.

## Deferred for v0.9.7.5 profiling
- Frequency of foreground-attribution usage-stats queries and AppOps checks.
- Detailed sysfs parsing CPU overhead, selective caching and minimal object
  allocations; optimize only after checking the new RECORDER_TIMING diagnostics.
- A/B workload matching, CPU scheduling interpretation and DUKO Report belong
  to analysis milestones, not stabilization.
