# CoreMetrics v0.3 stability + HUD Portal verification

**Do not re-run the full-device freeze scenario as a stress test.** Until a successful smoke test, use one recorder, and launch Eden BEFORE starting CoreMetrics via Quick Settings.

## Why this build
Nov 12 uploads included seven unique recordings: NFS completed 279 one-second samples; Metroid recording 7 exported 488 one-second samples while running; four recovered interruptions. Two duplicated files were identical. Metroid's launch involved sharp RAM usage changes; causal evidence for its crashes/freezes is still missing.

The SurfaceFlinger FPS attempt returned PERMISSION_DENIED on the Portal in all sessions. This build never invokes the denied system DUMP path; an FPS source remains unavailable. Net battery-terminal W continues to be recorded.

## A — Update and preserve data
1. Export important CSVs before updating.
2. Install v0.3 over the existing CoreMetrics APK without uninstalling; keep the same signing key. A forced uninstall removes database history.
3. Confirm previous sessions remain in Sessions and can be exported.
4. In Settings, copy the recorder diagnostics BEFORE doing further gameplay tests. It includes application-specific Android process-exit records. Provide this report for analysis.

## B — 60-second smoke test (no emulator)
1. Select 5 seconds in Live, leave HUD off and start recording.
2. Confirm REC tile/notification reflect recording and 12-ish samples are saved after one minute.
3. Stop from notification, verify single completed session, export CSV with power columns.
4. Repeat via Quick Settings tile. Android 13+ requires the user to add this tile explicitly from Live or Settings: choose Add QS tile and approve system prompt. If denied, edit Quick Settings manually and find CoreMetrics Record.
5. Confirm QS tile toggles Start/Stop and shows REC when active. Permission for notifications must be granted.

## C — HUD (opt-in)
1. Settings > Show HUD when recording = ON, tap Open overlay permission and grant Display over other apps. Permission is optional.
2. Start a 30-second recording OUTSIDE Eden. One small card should appear near upper-right containing CPU, GPU, CPU load, battery net W, FPS N/A. Drag the card.
3. Stop and verify card disappears immediately. Confirm Settings shows HUD status HUD_REMOVED.
4. Restart a session once and confirm only one card is present.
5. Leave HUD OFF for the first Eden smoke test, then repeat briefly with HUD ON after the no-HUD test succeeds. If HUD does not appear, Settings shows attach status and recorder journal includes HUD_START.

## D — Short Eden smoke test (if device is responsive)
1. Reboot the Portal if previous crash left it unstable; close O2P's recorder (O2P code unchanged).
2. Launch Eden and a known-stable NFS title; wait until game is fully playable.
3. Pull Quick Settings and start CoreMetrics, record 1-2 min at 5s intervals, HUD OFF.
4. Stop via Quick Settings and export CSV. If stable, try HUD ON for 30-60 seconds on NFS.
5. Only after that consider Metroid, with a 1-minute recording at 5s interval and HUD OFF initially. Avoid repeated restart loops if it freezes.

## If Eden or Portal crashes
- Do not assume CoreMetrics is responsible or that Eden deliberately closes all background apps. The OS may reclaim processes under memory pressure, and a GPU driver or emulator failure is also possible.
- After recovery, open CoreMetrics Settings and copy the process-exit + lifecycle report. Note which process crashed first if known, OEM mode, sampling interval and whether HUD was enabled.
- For system-wide freeze not explained by Android exit reasons, an ADB bugreport/logcat captured promptly after recovery can help diagnose GPU and system_server errors.

## Safeguards
- Battery W = net discharge at battery terminals, not SoC package power.
- FPS unavailable while system DUMP permission is denied. Other tools may require privileged access; a future read-only authorized provider requires separate design and device validation.
- 2- or 5-second polling only in this build; no additional telemetry sampler for HUD.
- No root, PServer, CPU/GPU writes, or changes to O2P Control Center.
