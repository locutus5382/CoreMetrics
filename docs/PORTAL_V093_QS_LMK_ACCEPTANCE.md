# CoreMetrics v0.9.3 — Quick Settings / LMK hardening

This is a **device-test candidate**, not a validated stable release. Keep main unchanged until Odin 2 Portal testing.

## Changes
- Both Android Quick Settings TileServices now run in the existing `:monitor` process. Opening a tile does not need to load the full Compose UI PID.
- Preserve private StatusProvider status checks even though they now execute within the tile's process: its `RecorderLease` fallback is needed to distinguish an idle service from a recoverable recording after low-memory process termination.
- `RecorderHud.show()` treats `root != null` as owned even while Android has not yet completed WindowManager attachment. When reusing a root, rebind the REC/lock/close callbacks; do not create a second overlay. A retained unlock chip is still cleaned up before creating a fresh root.
- The Compose UI polls the monitor's ContentProvider only while the monitor process is present (or Android cannot enumerate processes). A valid saved lease is still exposed as recoverable without creating a process.
- Optional **Close this screen after launching standby**, disabled by default, allows Android to reclaim cached UI memory. Finishing an Activity does **not** synchronously free the sampled 102 MiB PSS.
- Sessions now default to hiding recordings shorter than 15 seconds in the list. This filter does not delete any session, and existing SAF requests remain rememberSaveable.

## Important corrections to Gemini's suggestions
- Multiple installed application IDs have separate overlay owners; an in-process singleton cannot remove those other apps' HUDs.
- Android LOW_MEMORY exit reasons do not prove that Quick Settings initiated all the process restarts, nor that avoiding the UI process guarantees stability.
- **Do not** blindly replace all `startService(HUD_SHOW)` calls with `startForegroundService`: Android requires a promptly promoted FGS after each new foreground start and may reject background starts. ARM/RECOVER already use the foreground start path, with persisted-lease checks.
- Avoid removing `RecorderLease` recovery information by reading only the process-local `MonitorState`.
- 89 MiB and 102 MiB were *last sampled* process PSS values, not a permanent steady-state budget or a measured, guaranteed 53.4% drop.
- ChartTraceReducer is already wired into the primary diagnostics/compare plots; defer broader path caching until correctness/performance checks rather than introducing a chart regression into the HUD repair.
- Ko-fi promotion, unified public packaging, and changes to exported scorecards are separate release decisions, not prerequisites for stopping duplicate overlays.

## On-device acceptance checks
1. Use a **single** CoreMetrics application ID. Remove old v092/v093 test quick tiles and revoke overlay grants from other variants; do not delete app data containing recordings unless backed up.
2. Arm via UI and via CM HUD Quick Settings tile. Repeated taps during starting must never create a second interactive HUD.
3. Start Hades in Eden, tap REC, then tap CM HUD tile to hide and show while recording. Same session ID/sample count must continue; STOP saves exactly one session.
4. Simulate UI disappearance and Activity recreation, including SAF export document pickers, and confirm sessions remain available.
5. With adb, inspect `dumpsys activity processes`, `dumpsys window windows`, and the CoreMetrics lifecycle journal. Confirm the tile starts `:monitor` without booting the Compose UI PID and that only one overlay belongs to the installed package.
6. Repeat with the optional UI-finish switch on/off. Measure process PSS after launch and during the game; do not infer a fixed saving from older logs.
7. Review Crash/ANR, FGS start restrictions, QS tile recovery after game reload, and lease-expiry behavior. Android cannot promise automatic resurrection under LMK.

## Release gate
Do not merge or publicly label this beta until CI build/tests pass and the Hades/Quick Settings stress test succeeds on the actual Portal.