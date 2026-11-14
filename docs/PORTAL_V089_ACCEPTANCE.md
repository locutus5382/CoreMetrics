# CoreMetrics v0.8.9 — Odin 2 Portal Appearance Studio acceptance

## APK installation
- Install CoreMetrics v0.8.9 Frosted (app-v089base.apk) and Themes (app-v089themes.apk) on the Odin 2 Portal. These have separate app identities/data and may coexist; DO NOT run both recorder overlays together.
- Reuse an exported source CSV by importing it into each build, then verify single-session and A/B measurements are IDENTICAL in both (only UI differs).
- Check v0.8.9 Frosted vs v0.8.8.1 Frosted on the same screen. Original frosted background/card colors and default Material controls should remain recognizable; the pressed glint on custom CoreMetrics buttons is intentional.

## Theme gallery / identity
- In Themes APK, open Settings. There must be exactly four focusable horizontal-preview choices: Frosted Glass, True Glass, Modern Colorful, Technical Cockpit. Choose each using touch and A/DPAD_CENTER.
- Use quick "STYLE ↻" switch in header, confirm all four finishes cycle in order and selected finish persists across screen navigation, process recreation and cold app launch.
- Settings in Frosted-only APK must NOT expose theme selection or cycle; it should always show fixed Frosted.
- Confirm saved old style keys (crystal_aero / solar_pulse / cobalt_matrix) map correctly to their renamed finishes. Unknown keys safely use Frosted.

## Check every page at Portal landscape width
- Live: instrument cards, CPU/per-core topology, 60-second trace and telemetry status remain readable, no text loss or value substitution.
- Sessions: metadata and recording list buttons are visible with controller focus; session detail has range handles, stats and TXT/CSV exports.
- Compare: A/B scorecard B-A signs/units, custom overlap selection, chart cursor and selected-window report are intact.
- HUD Studio: permission and recording controls, sliders, telemetry visibility toggles, quick tiles and lock/unlock instructions remain accessible.
- Settings: privacy, theme preview gallery, immersive toggle and recorder diagnostics do not overlap or clip at default and increased font scaling.
- Scroll long settings/report pages, test all chip and slider selections with physical D-pad/analog and touch. Highlighted focus must be obvious.

## Appearance characteristics
- Frosted: original soft background, glass panes and branded cobalt/solar identity.
- True Glass: visible layered optical ribbons behind translucent readable cards, bright specular rims; no expensive real-time blur.
- Modern Colorful: distinguishable orange/cobalt primary controls, colorful highlight panels and slider track, high-contrast white text.
- Technical Cockpit: restrained matte navy frames, straight/small-radius cards, small ruler marks, monospace KPI/nav values.
- In all four finishes: selected range thumb, warnings, exports, measured samples and black/dark base remain readable. No false green-success badges for caution.
- Press/tap the STYLE/header action and rail item. A one-shot glint/scale is allowed, but NO periodic light sweeping, idle loops or janky main-thread animation.

## Recorder and performance regression
- Record an Eden session with overlay armed/locked. Switching app themes while CoreMetrics main Activity is open MUST NOT add hardware sampling, suspend recorder, alter recovery lease or force HUD to reappear after user-hiding it.
- Verify floating HUD is still the SAME native Android overlay independent of app style. The v0.8.8.1 in-app HUD visibility pause and intended return on game switch still work.
- Run a 10+ minute session while emulator is under normal memory pressure. Review Recorder Diagnostics and confirm no new crash, ANR or low-memory termination attributable to theme switching.
- Measure subjective screen scroll/switch responsiveness, plus CPU/RAM of UI if available. No claim that a static shader-free backdrop completely prevents system LMK.
- Verify SurfaceFlinger FPS is still unavailable where permission is missing; the theme does NOT invent FPS.

## Engineering exit gate
- GitHub Actions: testDebugUnitTest, assembleDebug, assembleV089base and assembleV089themes pass on the FINAL v0.8.9 commit and produce two matching APK artifacts.
- Report screenshots from each finish, especially Sessions → Analyze, Compare and HUD. Flag any less-readable alternative for removal/refinement.
- Do not merge PR until device acceptance. PNG scorecard and automatic deterministic findings remain future milestones, not part of this visual-only release.
