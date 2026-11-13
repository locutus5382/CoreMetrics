# CoreMetrics v0.8.2 — targeted regression on Odin 2 Portal

1. Install two packages from one successful **final-SHA** workflow: `app-v082base.apk` (Frosted) and `app-v082themes.apk` (Frosted default with Liquid/Tactical options). Export old sessions first. Never run two HUDs at once.
2. Sample with HUD enabled (REC): compare battery %, temperature and battery status sampled from the same sticky Android battery Intent; verify the discharge battery power output is unchanged from v0.8.1 within natural device variability.
3. After game starts, open compact HUD and record. Turn blinking off: the red/colored REC dot must stop toggling; turn it back on: it must resume. Dock to either screen edge: blink must stop while tab is docked; expand: resume. Repeat from settings outside HUD. No dropped HUD touch-control functionality.
4. Import a session whose first 30 s includes Stock-like ceilings transitioning to Large UC. Warm-up OFF shows initial observed phase; ON removes it from Observed Profile, Core Winner, Regime and A/B charts. Elapsed labels must continue referring to original session timestamps. Manual linked full timelines and raw CSV must not be trimmed.
5. Live CPU label review: Stock + Medium or Large UC limits warns; Underclocking + Stock ceilings warns with GPU-only caveat; Custom or unavailable ceilings do not warn. Do not infer tuner app identity.
6. Compare two charging/discharging recordings. Δ battery W shown as **sample-mean discharge only**; charging/unknown data excluded. Integrated Wh, mWh/min and weighted W must remain consistent with valid discharge intervals and real gaps.
7. Reopen both themes and toggle tabs/controller after touch input; no stale A/B card after selecting new sessions or warm-up toggle. Repeated 30-s traces should not retain hidden stale data.
8. Record before Eden starts and again after it starts; collect recorder journal and Android exit reasons for each. The source changes optimize CPU/allocations but DO NOT prevent memory kills.
9. Validate SQLite v4, 26/28-column CSV, QS HUD vs REC tiles, locked independent unlock chip and notification escape.
10. Do not merge to main or publish to Play until physical lock behavior, process resilience, battery correctness and signed release policy checks have passed.
