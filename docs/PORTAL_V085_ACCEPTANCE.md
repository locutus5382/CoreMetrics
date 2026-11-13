# CoreMetrics v0.8.5 — Odin 2 Portal physical-device acceptance

All code/CI conclusions are provisional until actual Portal tests. Hardware source remains read-only.

## Installation and isolation
- The **Frosted** APK (`app-v085base.apk`) contains all v0.8.5 functionality but has only Frosted Glass, to compare the usefulness of interface changes without visual novelty.
- The **Themes** APK (`app-v085themes.apk`) has the identical telemetry and analysis behavior and four finishes (Frosted, Crystal Aero, Solar Pulse, Cobalt Matrix).
- New package names `...v085base` / `...v085themes` keep existing v0.8.4 SQLite separate. Export any important earlier sessions to CSV before uninstalling anything. Do not run both recorders simultaneously.
- Do not confuse a successful Gradle build with valid Android overlay behavior.

## HUD lifecycle (top priority)
1. Game first: start Eden, load Metroid or Mario Kart, then enable CM HUD in Android Quick Settings. Expect cyan/active ON tile; HUD visible, with NO telemetry in standby.
2. Tap REC inside HUD. Verify real samples arrive and notification stays active.
3. Tap **CM HUD tile during REC**. HUD disappears, **session ID, interval, SQLite writing and notification stay active**, tile is inactive with label `HIDDEN REC`. Restore using same tile. Never STOP when hiding.
4. Hide via the HUD's **Dock** control: main panel becomes existing 12dp edge tab with approx 44dp hit area; tapping tab returns panel. Repeat both edges.
5. Lock/unlock independent 48dp chip before and after docking/restore. Notification fallback must recover the HUD. Never trap a locked GONE window.
6. After a session ends while HUD hidden, standby service should release idle resources; all samples and final status saved.
7. After Android LOW_MEMORY kill, tile must read actual status at next expansion; no silent fake REC/ON state. Keep `CM REC` tile for safety until this is proven.

## Adaptive colors
8. With Adaptive Metric Colors OFF, all metric text uses normal white/ice-blue, regardless of temperature or load.
9. With ON, use recorded samples and verify CPU/GPU temperature thresholds: 65°C yellow, 80°C red. These are appearance bands, NOT safety limits. Check release hysteresis around 63/78°C. High utilization is yellow only; normal utilization must never become critical red by itself.
10. RAM pressure must use Android's `lowMemory` and `threshold` fields; 80%+ system RAM used is not sufficient evidence of a dangerous condition. With unknown readings, remain neutral. No extra timer added.
11. Switch between pill and rounded-card/Pro, adjust opacity and size, check button visibility, proper lock position and landscape clipping. No micrographs or `Graphs` toggle remain.

## Analytics
12. Single session: inspect CPU temp + CPU load as two synchronized tracks. Change either metric to GPU temp/load, battery W, or RAM. Tap/drag: exact nearest measured readings appear. Under charging, battery-W track has gaps, not fabricated discharge.
13. Use a session that contains a telemetry gap. Graph path must BREAK at the gap. With Skip First 30s ON, cursor elapsed labels must remain relative to original first sample, not reset to zero.
14. Compare a 670s session to an 80s session. Default chart shows the overlapping recorded duration, not a default scrub position at 474s where B is missing; user can explicitly switch to full view.
15. Observe side-by-side A/B summary, B-A differences and caveats when durations, GPU load, status or driver/resolution differ. Telemetry integrity is not equal to benchmark comparability; quality 100/100 doesn't establish matched gaming scenes.
16. Expand Technical evidence to find regimes, energy source, power status/coverage and core-winner diagnostics. Old CSVs with tied winners remain unchanged.
17. Validate import/export of historic 26- and 28-column CSVs and manual linked recordings with actual interruption gaps.

## Themes and navigation
18. Frosted build must have no theme selector, preserved original diffused materials, and a subtle one-shot press/focus glint (not constant animation).
19. Themes build should show four finishes on every app screen; cycle from header and select directly under Settings. Crystal Aero panels translucent over STATIC Cobalt/Cyan/Orange/Gold ribbons, Solar Pulse solid high-contrast colorful controls, Cobalt Matrix grid and ticked gauges. **Native in-game HUD remains same**.
20. Test Touch, D-pad, A activate, shoulder tabs, right-stick scrolling and synchronized Compare scrub on both builds, mixed with touch. Screen-reader hints and tap targets must stay readable.
21. Capture screenshots of single-session analysis, A/B top and scrubber, both HUD layouts, and all four finishes.

## Release gates
- GitHub Actions testDebugUnitTest, assembleDebug, assembleV085base, assembleV085themes all green on **final source SHA**; verify two named downloadable artifacts.
- Actual Portal overlay lock/dock/tile and REC continuity verified. No new app-introduced crashes, input focus failures, or unexplained dropped samples.
- Still draft/not merged into main; signing, Android foreground-service policy and Play privacy/compliance checks remain separate work.
