# CoreMetrics v0.8.4 — visual and physical-controller acceptance

## Install/test packages
- **Daily test:** `CoreMetrics-v0.8.4-standard-debug` (`app-debug.apk`) — all 3 finishes from the header; package `com.locutus5382.coremetrics`.
- **Reference:** `CoreMetrics-v0.8.4-analytics-only` (`app-v084base.apk`) — Frosted only; separate package `com.locutus5382.coremetrics.v084base`.
- **Isolated theme test:** `CoreMetrics-v0.8.4-themes` (`app-v084themes.apk`) — all 3 finishes; package `com.locutus5382.coremetrics.v084themes`.
- Do not activate more than one recorder/HUD at a time. The three APKs have separate SQLite databases, so export/import historical CSVs deliberately. Retain older versions until data are backed up.

## Acceptance steps
1. **HUD at the right bezel:** Eden-first, arm HUD through Quick Settings, tap REC and open gear. The drawer shows CPU T / GPU T / CPU % / GPU % on row one and RAM / W / Graphs / STOP on row two, with a small opacity +/- footer. No metric or 48dp independent lock chip may vanish beyond the right edge. Repeat after dragging toward the left bezel.
2. **Visibility:** Active chips show ✓ with orange/cobalt fill and visible border; inactive chips show ○ and muted/borderless background. Toggle all metrics and verify the summary reflects their actual display state. Graphs only render existing recorded samples. Opacity percent changes and persists when rearmed. Red STOP is obvious during recording.
3. **No fake FPS:** Saved HUD FPS=ON from a previous build must not produce `FPS —`. No quick FPS control. If a verified source is never present, no FPS sparkline, frame time or numerical layer FPS may be invented.
4. **Compact pill and lock:** Compact HUD text stays one line and may ellipsize with many metrics. Switch to Pro to inspect multiline metrics. Lock while drawer open must collapse drawer and retain the separate touchable unlock window. The 48dp *touch area* remains usable even though the drawn glass badge is shorter.
5. **Controller:** Set Odin's controller to gamepad mode. Press L1/R1 for tabs; D-pad navigates with a visible gold focus ring; A activates; D-pad left/right on a focused Slider adjusts it. Right-stick up/down actually scrolls the page, and horizontal input on Compare moves the synced A/B cursor. Verify after mixing touch and controller; confirm focus does not jump to the nonclickable root.
6. **Live dashboard:** When recording, gauges show °C/Watts/GiB plus CPU/GPU load, battery charging status and memory-total subtitles. Live actions now fit in the header, and redundant lower-row cards are gone. When idle/no recording, unavailable readouts are honest, not zero; no new polling. `LiveScope` and per-core matrix remain available by scrolling.
7. **Sessions:** Energy and Observed Profile cards appear side by side; busiest-core winners are shown in two groups of four. History has left metadata and right-aligned CSV / Analyze / Tags / Report actions without horizontal scrolling.
8. **Themes:** In standard debug and Themes variant, header button cycles Frosted / Liquid / Tactical and Settings persists selection. Analytics-only shows `FROSTED · FIXED`. Switch when recording and check that **only Compose app screens change**, not in-game native HUD style, hardware polling or sample cadence.
9. **Optional immersive:** Settings switch defaults OFF. Turn ON, verify status and navigation bars hide; swipe from screen edge to reveal transient bars and return to Settings to disable. Confirm Android accessibility/system navigation remains possible. It does not change how the floating game HUD is displayed.
10. **Low memory:** Compare Eden-first (Quick Settings HUD/REC) versus HUD-first (load Eden). Record process-exit diagnostics. A background kill can still occur; do not call this release a fix for Android LMK. STOP must save session and independent lock must still unlock.
11. **Data continuity:** CSV 26-/28-column import, raw session timestamps and statuses, manually linked gap, energy/charging exclusions and warm-up filter all unchanged. Historic CPU0-dominated sessions are not rewritten by the v0.8.3 unique-core tie fix.
12. **Release:** Verify green CI at the FINAL code SHA and all three named artifacts. Do not merge to main or publish without physical Portal regression, privacy review and signed Play release compliance.

## Report
Please include package variant, controller mode, whether joystick scroll/scrub worked, screenshot of 2×4 drawer at the screen edge, whether the hidden bars could be restored, and recorder diagnostics from the latest app.
