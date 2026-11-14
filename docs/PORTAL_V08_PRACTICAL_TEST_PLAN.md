# CoreMetrics v0.8 — practical add-ons acceptance tests

## Build and installation
- This branch builds **two APKs** with separate Android application IDs: `app-v08base.apk` (`com.locutus5382.coremetrics.v08base`) and `app-v08themes.apk` (`com.locutus5382.coremetrics.v08themes`). Artifacts: `CoreMetrics-v0.8-analytics-only` and `CoreMetrics-v0.8-themes`. Use a final SHA **green CI** run, not a stale earlier build.
- Both can coexist with v0.7 and each other. Databases are isolated; export older CoreMetrics CSV and import it into whichever version you're testing. Only one HUD/recorder should be armed at any given time.
- Both use `Session 1`, `Session 2`, etc. and Stock / Underclocking / Custom selection. New builds start with an empty optional game/track note.

## Metadata and history
1. On a new session, open Sessions > Tags; select Turnip and Handheld 1x with a single press. Select Custom separately for a named graphics driver version and a named resolution/scale. Verify the labels survive a cold restart.
2. Select None to clear either tag. Re-import a **26-column** legacy CSV; tags should remain unknown. Export a tagged run and import its **28-column** CSV into the other variant: tags, original measurement rows, manual label and completion status must roundtrip. No extra sample fields are synthesized.
3. Compare runs with different or missing driver/resolution tags. Reports must show manually entered context and caution; never infer which driver is active from measured GPU load.
4. Toggle Hide runs <15s. Verify that short accidental recordings disappear from Sessions **only**; their database samples, original CSV export and Compare session selection remain intact.

## Power and warm-up
5. Discharging session: integrated Wh, mWh/min, time-weighted W and discharge coverage are consistent.
6. When plugged in/charging, the session should show **PLUGGED IN**, mixed intervals should show **MIXED POWER**. Battery drain Wh must not integrate across the charging/discharge boundary; plugged-only sessions report unavailable rather than zero. Unknown status must not be labeled plugged.
7. Enable Skip first 30s; compare the same session's thermal mean, power rate and sample count with it disabled. The first 30s refers to initial REC, not proven game loading. A short run can become insufficient for meaningful analysis.
8. Export session and A/B reports with and without warm-up selected; verify filters are described. Linked Sessions Timeline must still show the complete absolute-time source histories and gaps. Raw CSV remains unchanged.

## Controller and Quick Settings
9. In each of the five tabs, press L1 / R1 to switch tabs. Mix touchscreen taps and controller input; verify shoulder controls recover from touch mode.
10. D-pad navigate/activate session chips and metadata presets; focus the A/B Slider and adjust scrub with D-pad left/right. Check focus indicators and usable controls in 360dp landscape, including both themes.
11. From HUD Studio request the dedicated **Add HUD tile**. Compare its icon/label with **Add REC tile**. HUD OFF -> ON must create no samples, ON -> OFF closes standby only, and the HUD tile must **not stop an active recording**. REC tile must continue to start/stop recordings. Review notifications/permissions if the tile refuses to arm.
12. Lock/unlock the in-game native HUD with the independent flat vector chip while Eden is running, and test edge docking. Themes must not affect overlay touch behavior or memory stability.

## Theme comparison and release gate
13. Analytics-only remains Frosted. Themes app defaults to Frosted; change to Liquid then Tactical then back to Frosted. Inspect readability and input focus on Portal. No text should be blurred by styling. Both APKs must use the same analytics output given the same imported CSV.
14. Repeat the game-first recorder test and collect LMK exit reasons. No privileged FPS provider, battery manipulation, OOM priority or unprivileged app-background hacks are enabled.
15. Do not merge into main / Play Store before real-device controller, HUD, power-status and import/migration tests pass.
