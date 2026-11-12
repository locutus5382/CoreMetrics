# CoreMetrics v0.2 — Odin 2 Portal device test

This version is not yet validated as an FPS tool.

## A. Install and preserve old sessions
- Export existing NFS session CSVs before installing, for backup.
- Update over the prior build if signing permits. Do not uninstall unless necessary: uninstalling clears app-private SQLite recordings.
- Confirm that the previous three NFS sessions remain visible after the v1-to-v2 database migration.
- Keep O2P Control Center's recorder stopped. Do not record with two apps simultaneously during a benchmark.

## B. Recorder stability
- Choose a unique label and 5 s sampling. Start from the Live screen.
- Confirm the REC indicator and persistent notification. Record 45 seconds.
- Stop using Quick Settings, then export a CSV and confirm one completed session.
- Check Settings / recorder diagnostics for START_REQUESTED, SESSION_PERSISTED, FIRST_SAMPLE_WRITTEN, STOP_REQUESTED, and SESSION_FINALIZED.
- Start another session before launching Eden; verify Start is acknowledged, then launch NFS. If the service is killed, next monitor restart should mark the saved earlier session INTERRUPTED_RECOVERED without discarding existing samples.
- Copy the CoreMetrics diagnostics report after any failure. O2P's separate process-death history does not establish a CoreMetrics kill.

## C. Net battery watts
- With charger disconnected, sample five readings at idle. Observe battery_net_w, battery_current_ua, battery_voltage_mv and battery_power_status in exported CSV.
- Under load, net battery watts should respond, if firmware exposes current/voltage correctly. Positive watts mean net discharge (battery to device); negative watts mean net charging.
- These are battery-terminal measurements, not wall power or SoC/package power. Charging or bypass invalidates simple cross-run power comparisons.

## D. SurfaceFlinger frame test
- In Settings enable experimental SurfaceFlinger FPS for the NEXT session, and grant optional Usage Access so Eden can be identified.
- Start a session, run Eden 25+ seconds and inspect FPS source/status.
- PERMISSION_DENIED means the Android app UID cannot access the protected system compositor. This is not an FPS reading of 0.
- NO_MATCHING_GAME_LAYER or AMBIGUOUS_GAME_LAYERS means automatic layer selection was not verified.
- If a non-null FPS is returned, cross-check against Eden's in-game FPS counter on a steady scene. It is a SurfaceFlinger layer-present rate, not emulator internal game FPS.
- Turn probing OFF for low-overhead benchmark tests until its accuracy and sampling overhead are validated.

## E. NFS comparison
- Same car, track, game driver, settings, brightness, fan and firmware performance mode.
- Four runs Stock / Large Underclock / Large Underclock / Stock with near-equal initial temperatures.
- Compare tab: optional Eden foreground and GPU >= 20 percent filter is only an approximate race heuristic. It does not synchronize actual scene content.
- Check CPU7 load, observed policy3 and policy7 maximum clocks, battery net power and FPS availability.

## Safety
- No hardware writes, root, PServer, oom_score_adj tuning, internet permission or automatic boot recorder.
- O2P Control Center is untouched.
