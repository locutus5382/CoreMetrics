# CoreMetrics v0.4 — standby HUD acceptance tests (Odin 2 Portal)

**Install only after CI shows Success.** This remains a device-untested alpha.

## Preserve recordings
Export old CSVs. Update over the existing application if the signing key matches.
Do not uninstall before backup: uninstall deletes app-private SQLite data.

## A. Standby overlay without Eden
1. Open HUD, grant Display over other apps and notifications permissions.
2. Select Medium / Compact pill / 57% opacity, CPU/GPU temperatures, RAM and Battery W, blinking Red.
3. Tap Launch standby. HUD shows READY / no sampling, REC and X.
4. Wait 60 seconds. No new session should exist, and there should be no sensor/database sampling.
5. Drag the capsule by its telemetry area. Close with X and verify that notification disappears.
6. Re-arm. Change opacity to 30%, size Large, layout Pro, select CPU/GPU loads, turn on the sparkline. Check live changes and fit on landscape screen.

## B. In-game-compatible REC and STOP
1. With standby visible, tap REC in the pill.
2. Confirm the button changes to STOP with elapsed time, the small colored dot blinks and the foreground notification says recording.
3. Record 30–60 seconds. Tap STOP on the pill; inspect Completed session and CSV in Sessions.
4. HUD returns to READY; earlier sparkline history clears. Start a second session from the same pill and confirm one overlay/one recording.
5. Close standby via X. Open Settings diagnostics if any step fails.

## C. Eden smoke test (NFS before Metroid)
1. Turn OFF O2P Control Center's recorder, reboot if prior freeze caused instability.
2. Arm CoreMetrics standby. It should do no sensor sampling during Eden launch.
3. Start NFS in Eden and wait until playable; tap REC on the floating pill.
4. Record 1 minute at 5-second interval; tap STOP and verify a completed recording.
5. Try Pro HUD in NFS if stable. Only then consider a short Metroid trial. Do not repeatedly provoke full-device freezes.

## Measurement and platform limits
- Standby shows no fabricated CPU/GPU/FPS values. Battery W is battery-terminal NET power, not CPU power.
- FPS remains unavailable: the ordinary Android app UID was denied SurfaceFlinger access. No repeated dumpsys, PServer, root, hardware writes or process-priority changes.
- Only the recording dot has a 650-ms blink; actual telemetry and sparkline use the same 2- or 5-second sample loop. No extra polling process.
- Standby requires a visible foreground service/notification; Android may still kill it under memory pressure.
- Android 14+ specialUse foreground service and overlay behavior still require Google Play policy review; compilation alone is not permission to publish.

## Design choices to confirm with the owner
- Standby no measurements (safe default) versus opt-in 10–15-second standby telemetry.
- Quick Settings tile toggles REC (current) versus arms/disarms the HUD.
- On STOP: stay in standby (current) versus hide HUD.
- Allowed opacity 30–90% (current) versus complete transparency.
- Explicit Compact/Pro layouts (current) versus tap-to-expand.
