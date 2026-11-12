# CoreMetrics v0.7 Portal validation checklist

## Installation
- Download the **successful final-sha** GitHub Actions artifact `CoreMetrics-v0.7-test-side-by-side`; extract `app-v07test.apk`. Its package is `com.locutus5382.coremetrics.v07test`. Do not uninstall the earlier Preview: they can coexist but do not share private session data.
- Export existing recordings before upgrades and import the supplied `CoreMetrics_1 (3).csv` and `CoreMetrics_2 (3).csv` into v0.7 Test. Only one HUD/recorder should run at a time.

## Lock/unlock regression
1. Start Mario Kart first, then launch CoreMetrics v0.7 HUD.
2. Tap 🔓; the telemetry body must be **fully click-through**, while the new independent 48dp 🔒 chip near the original icon must remain clickable. Tap the chip and verify main HUD REC/drag/⚙ work again.
3. Repeat when recording and after changing opacity and HUD layout, near both screen edges. On lock failure the main HUD must remain touchable. On STOP/disarm the chip must disappear.
4. The notification **Unlock HUD** and HUD Studio are recovery controls. If Android says `Untrusted touch due to occlusion`, report it without disabling system protection.

## Low-memory service and session resilience
1. Compare *game first → HUD* with *HUD first → Eden/game*. Record lifecycle events and Android exit reasons.
2. If the :monitor process is terminated for `LOW_MEMORY`, Android **might** restart a START_STICKY foreground service. If it restarts within a valid lease, inspect `RESTART_ACCEPTED` and `SESSION_RESUMED` with the **same session ID** and a visible recording gap. The user must never see fabricated samples.
3. Test STOP/DISARM: there must be no unsolicited recording restart. Verify stale standby lease beyond 10 min and recording lease beyond 90 s are rejected, and no more than two restarts occur. Reboot must not restore recordings.
4. Diagnostic exit history now reports `lastPssMiB`, not `pssKiB`. It is last sampled memory, **not** exact RAM usage at kill. The new report-time memory snapshot is not historical.

## Five analytics checks
1. Import historical Mario Kart sessions. Expected: 16 samples / INTERRUPTED_RECOVERED / ~30s, and 72 samples / COMPLETED / ~142s. Both have a **Stock label conflicting with observed 1.79/1.84GHz limits**.
2. Inspect Analyze / Compare reliability cards. Session 1 should score poorly due to interruption and short duration; second should be more suitable. Scores are disclosed heuristics, not statistical confidence or proof of performance.
3. Integrate battery-terminal power: expect ~**0.0289 Wh** for session 1 and ~**0.1611 Wh** for session 2. Verify no extrapolation over missing timestamps, charging or bypass.
4. Inspect thermal trend: a last 60-second regression may identify warming, cooling or a conservative candidate steady state. Do not infer throttling from temperature alone.
5. Export a single-session evidence report and a two-session A/B report. They must include source/provenance and limitations; FPS/W, energy per actual game frame, 1% low and frame-pacing stability stay unavailable without validated FPS.
6. Compare equal gameplay segments when possible; different scene/load, battery/charge conditions and run length can confound A/B results.

## On-device limitations
- Even START_STICKY does **not** guarantee an Android/OEM LMK survivor. If launch-time kills persist, use game-first workflow; never claim the issue is solved without test evidence.
- The privileged FPS Lab remains isolated and off. There is no verified PServer host or actual presented-frame source on this Portal yet.
