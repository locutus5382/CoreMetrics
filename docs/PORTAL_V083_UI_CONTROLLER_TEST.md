# CoreMetrics v0.8.3 — Odin 2 Portal UI and controller acceptance

## Source evidence / interpretation

- Original v0.8.2 diagnostic report (user-supplied 13 Nov 2026) reported LOW_MEMORY exits for both `com.locutus5382.coremetrics.v082themes` and `:monitor` while Eden launched. Prior PSS readings: UI 116 MiB, service 63 MiB. PSS reported as 0 is **unavailable sampling**, not zero consumed memory. Report-time available RAM (~3,999 MiB) was *later*, so not a contradiction.
- The journal contained successful `HUD_LOCKED_WITH_TOUCHABLE_UNLOCK` and `HUD_UNLOCKED_TOUCH_CONTROLS_RESTORED` transitions; sessions 1, 2, 3, 4 finalized `COMPLETED` (20, 44, 3, 6 samples).
- FPS ~60 in screenshots is from a separate app's overlay, **not** CoreMetrics' currently disabled privileged FPS provider.

## Test sequence

1. **Install and identify the variants:** Build final branch SHA with debug unit tests + Analytics + Themes. In Analytics expect header text `FROSTED · FIXED`; Settings explains it is single-style. In Themes expect top-right `THEME · Frosted Glass ↻`. Tap repeatedly; see immediate background/card changes among Frosted, Liquid and Tactical. Settings should show CURRENT theme and three direct choices. Reopen app and confirm theme persists. The native in-game HUD should NOT change its base finish when switching app-screen themes.
2. **No-overflow drawer:** Open Eden gameplay, then the HUD from Quick Settings. Expand ⚙. No vital button (gear, REC/STOP, lock, independent unlock) may disappear outside the visible viewport. Swipe metrics and action rows horizontally. Test 475dp width at regular and large HUD sizes, both landscape edges, and Pro layout.
3. **Visual ON/OFF feedback:** Toggle CPU T / GPU T / CPU % / GPU % / FPS / W / RAM. Active controls show bright cyan frosted and ●; inactive muted/borderless ○. `Graphs` shows active state; Glass percent updates immediately with ±. REC during a session is distinctly red. FPS may show `—` as unavailable; toggling it does not create an FPS provider.
4. **Lock regression:** Lock and unlock 10 times while recording; verify independent 48dp lock chip remains touchable and game touches pass through main HUD. Lock while drawer open should close it and not trap controls. Notification unlock remains available.
5. **Physical controller (gamepad mode):** Navigate Live → Sessions → Compare → HUD → Settings using L1/R1 (R2 mapping optional). Gold focus outline should initially appear on Live. DPAD_UP/DOWN/LEFT/RIGHT and directional hat should move selection among focusable controls, not remain trapped in the root container. Press controller A to activate focused button/chip. Focus the Compare scrub slider, use left/right to adjust without changing tabs. Move right stick along both axes: it should move focus through the interface, but mapping depends on Portal device mode. Capture screenshots/actual key codes if this fails.
6. **Sessions visual balance:** With a completed recording open detail. Energy and Selected-vs-Observed cards occupy side-by-side areas in landscape; Busiest Core chart uses the content width. Compare provides the same layout. Ensure scroll isn't clipped by system navigation bars.
7. **Busiest CPU accuracy:** Run new session under light load and gameplay. If all eight sampled loads are zero or the maximum tied, the analyzer must report no winner for that tick rather than always assigning CPU0. Old records remain unchanged and may still show inflated CPU0 shares. Compare a newly recorded session to the old Session 2.
8. **Eden launch / LMK:** Test game-first QS HUD + REC then stop, and HUD-first followed by Eden launch. Gather new Android process exits and recorder journal. Do not conclude that foreground notification or START_STICKY ensures survival. Repeated LOW_MEMORY crashes are a platform/device constraint unless profiling finds application-specific memory growth.
9. **Data integrity:** Export completed sessions, verify raw sample timestamps and statuses; check both 26- and 28-column CSV imports and reversible session links. Each APK has its own SQLite database. Do not uninstall historical packages until sessions are exported.
10. **Release gate:** All branch CI jobs green at final SHA, plus physical controller, drawer, lock and simultaneous Eden test pass. Keep PR draft and do not ship to Google Play until proper production signing and Android specialUse FGS policy approval.

## Test feedback to report
- Which exact APK (Analytics or Themes) and Android/controller mode you used
- Whether you can see the header THEME control, and whether Tactical becomes opaque
- Whether every HUD control remains visible while drawer open at the right and left edge
- Whether D-pad keys vs hat axes vs right stick move a visible gold focus outline; whether A activates
- One new `CoreMetrics v0.8.3 recorder diagnostics` report after launching Eden
