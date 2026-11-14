# CoreMetrics

**CoreMetrics** is an independent, offline-first Android gameplay telemetry and session-analysis application, complementary to [O2P Control Center](https://github.com/locutus5382/O2P-Control-Center). Its purpose is to measure and compare *what actually happens* with different CPU/GPU profiles — without modifying them.

**Current development status: v0.9.6.2 unified alpha — one canonical CoreMetrics application ID, four optional finishes, with both debug and R8-optimized test builds.** This is a test candidate, not a public release. GitHub CI and real Odin 2 Portal acceptance are required before merging into `main`.

## One-app Quick Start (v0.9.6.2)

1. For fault diagnosis install `CoreMetrics-v0.9.6.2-unified-debug` (`app-debug.apk`). For realistic optimized size/startup testing, install **instead** `CoreMetrics-v0.9.6.2-optimized-test` (`app-optimizedTest.apk`, R8-minified, signed with CI's temporary Android debug key). Both use package `com.locutus5382.coremetrics`; they cannot coexist. The initial in-app guide explains notification, overlay and Usage Access permissions. Back up recordings before any signing-key-related uninstall.
2. Launch Eden/Hades, open Android Quick Settings and tap **CoreMetrics HUD**. The thin outlined pulse badge becomes a bold illuminated badge in the system accent; the status reads **Ready · Tap REC**.
3. Tap the floating HUD's **REC** button, or the separate **CoreMetrics REC** tile, to start sampling. The HUD tile only shows/hides the overlay; the REC tile explicitly starts/stops the recording. Both tiles describe active recording even if the HUD is hidden.
4. Long-press the HUD tile to open CoreMetrics' HUD Studio. After recording, open **Sessions / Compare** for local offline analysis.

**Appearance:** Frosted Glass is the default. True Glass, Modern Colorful and Technical Cockpit are available in Settings or through **STYLE ↻**. Themes do not alter telemetry, the floating HUD or sample cadence. The system, not CoreMetrics, controls the active Quick Settings tile color; there is no perpetual glow animation or live tile-temperature polling.

**RAM and stability changes:** QS tiles read their own `:monitor` process state directly while checking persisted leases for recovery, without unnecessary self-ContentProvider IPC. The UI polls less often outside Live/HUD screens and caches thermal-sensor classification during recorded sampling. Closing the Activity after arming standby remains optional; **no fixed memory saving or LMK immunity is claimed**.

**Migration warning:** Older `.v092themes`, `.v093base`, `.v093themes`, `.preview`, and other side-by-side test variants remain distinct Android packages with **separate SQLite session databases**. Installing the unified APK will NOT import their private sessions. Export any valuable CSVs and diagnostic reports from old builds first, then import CSVs into the unified application. Remove old Quick Settings tiles, revoke older variants' overlay grants and uninstall variants only AFTER preserving data. The canonical package `com.locutus5382.coremetrics` retains its own app-private data when updated with a compatible signing key; a different signing key may require uninstall/reinstall and backup first.

**Validation:** See `docs/PORTAL_V0962_UNIFIED_ACCEPTANCE.md` (and the earlier v0.9.6.1 checklist for onboarding and migration). Source review and successful GitHub unit builds do not substitute for 8 GB Odin 2 Portal real-game testing.

---


## v0.9.6.2 — Packaging, streaming sampler and stability

- **Remove the oversized extended Material icon dependency:** Five custom, compact original XML vector navigation icons replace a whole icon pack; no bundled native binaries, fonts or external network dependencies are added. Compare **actual** APK sizes from CI rather than assume a specific reduction.
- **Installable optimized test build:** GitHub Actions builds the normal debuggable APK, the unsigned R8 release, and a separate R8-optimized test APK signed with Android's default debug key. CI test signatures are **not stable across fresh runners**. No in-place update guarantee between separate GitHub Action runs or prior debug installations.
- **Less Linux procfs overhead:** CPU statistics are streamed until the first non-CPU line (not hardcoded eight cores). Memory statistics stop after both `MemTotal` and `MemAvailable` are found. Invalid readings remain unavailable. JVM tests exercise reordered/invalid data and many-core devices.
- **CPU and thermal I/O:** Successful `cpuinfo_max_freq` hardware maxima are cached during a sampler lifetime; read live `scaling_max_freq` unchanged. Thermal zones are enumerated once with a bounded fallback for devices that restrict directory listing.
- **A/B swap without redundant sample queries:** Selected session IDs and loaded sample lists stay together, and cancelled/stale asynchronous reads do not replace newer selections. Scores and exports still use original database samples.
- **PNG export cleanup:** A per-export mutable rectangle and cached typefaces avoid repeated allocations. The 8-row table, 1600×1050 PNG, source labels and no-donation reporting policy are unchanged.
- **Finalization ordering:** Persist final recording status before clearing the fresh recovery lease. This reduces a narrow crash window in which previously persisted `RECORDING` samples might lose immediate session-recovery eligibility. Explicit STOP remains terminal. Keep single HUD owner and service lifecycle behavior intact.
- **Do not infer performance improvement from APK size:** Only on-device PSS, CPU I/O timings, first-sample latency and Hades/driver tests can demonstrate measurable RAM/overhead savings. No sampler interval or hardware write changes.

## v0.9.6 — Session management, analysis and optional support

- **Free for everyone:** No paywall, paid plan, donation-exclusive APK, gated feature or in-app purchase. Every function remains available whether or not anyone contributes.
- **Voluntary Ko-fi:** [Support CoreMetrics](https://ko-fi.com/locutus5382) if you want to. Settings opens the external browser; the app itself keeps **zero INTERNET permission** and no donation SDK. GitHub's FUNDING button points to the same page. Exported PNG reports contain **no donation link**.
- **Session maintenance:** After recording, edit game note, configuration, driver and render scale. One recording may be deleted only after a deliberate second confirmation. Sample rows and A/B links are removed atomically, while active RECORDING rows are protected.
- **Faster historical access:** Direct `sessionById` lookups replace arbitrary `list(200)` scans for individual reports and exports, reducing work and eliminating a historical session age limit in these lookups.
- **Improved comparison:** Busiest-core P50 is visible in the 8-row A/B PNG table; the A/B scrubber additionally supports observed busiest-core %, P3 and P7 clocks when present. These cannot prove a graphics driver caused a temperature or performance difference.
- **Lighter UI:** The Activity drops heavy source/analysis datasets when their pages are closed, fixes the finish-after-arming visibility transition, and caches the remaining session signal chart path.
- **Release shrink:** R8 minification and resource shrinking are applied to release builds; the debug APK remains unminified. Build success does not measure runtime RAM or imply OEM LMK immunity.
- **Still required:** real Portal acceptance, signing-key/data migration plan, Play foreground-service review and device profiling before marking stable.

## Planned product
- **Live:** CPU/SoC and GPU temperature, CPU/GPU load and clocks, RAM, battery. All readings identified by source, with explicit Unavailable fallback.
- **Sessions:** User-started foreground recording with a persistent Stop notification, local SQLite storage and CSV exports.
- **Compare:** Stock / ClusterTune / Custom run labels, elapsed-time-aligned thermal and utilization charts and differences. A label is user-entered, never proof that a tuner is active.
- **HUD Studio:** No-sampling standby, tap REC/STOP to record and save. Pill or rounded-card layouts, size, opacity, metric visibility, dock/minimize handle, blink indicator and optional per-metric adaptive severity colors. Tiny in-game sparklines are deliberately removed; post-session analysis retains raw recorded values.
- **FPS:** Experimental SurfaceFlinger access remains OFF/unverified on the Portal. Standard app UID may lack DUMP permission; no gameplay FPS, percentile FPS, or fabricated frame graph is displayed. Never confuse display refresh with game rendering.

## Security, privacy and platform
- Separate Android application ID: `com.locutus5382.coremetrics`. Separate database and notifications; no dependency on O2P.
- No `INTERNET` permission, analytics SDK, account, boot receiver, automatic background recorder, Accessibility service, kernel writes, frequency modifications or `oom_score_adj` changes. Optional `SYSTEM_ALERT_WINDOW` permission is needed only for the user-enabled, single-window HUD.
- Optional Usage Access for identifying app packages. It cannot identify a game running inside an emulator.
- Google Play build baseline uses **compileSdk/targetSdk 36**, minSdk 31. User-initiated `specialUse` foreground recording must be approved by Play Console. Neither target SDK nor a manifest declaration guarantees approval.
- No PServer in initial Play-compatible version. A separate strictly read-only Odin variant may be designed later only if it provides validated value and clear permissions; a read-only observer cannot claim LMK immunity.
- On-device collected readings and session history stay in app-private storage unless the user chooses to export them.

**Historical note:** Previous development stages used separate Preview / Frosted / Themes packages to compare builds. The unified v0.9.6 build replaces this workflow; historical test instructions below are retained only for traceability.

## Optional v0.8 Themes build (separate side-by-side APK)

Built on the **same v0.8 analytics implementation**, adding the three UI finishes from Gemini's visual design reference:
- **Frosted Glass (default):** original CoreMetrics cobalt/navy/orange glass gradients and cards **unchanged**.
- **Liquid Glass:** static high-contrast navy panels with brighter cobalt/specular hairline borders; no blur applied to the card's content, no per-frame animated sheen or forced compositor layer.
- **Tactical Flat:** OLED black background, opaque slate cards, 1dp borders and mint/cyan/orange gauge arcs.

The selector is in the **Settings** screen only of `com.locutus5382.coremetrics.v08themes`. It persists for that separate app; the baseline `v08base` package never displays it, and always uses Frosted Glass. HUD/WindowManager overlays, recorder, SQLite schema, permissions and FPS Lab are identical to the baseline and remain outside theme scope. Screenshots are conceptual references, not pixel-perfect contracts for the Odin's landscape display.

This build deliberately **does not copy** Gemini's `.blur(20.dp)` card modifier (that would blur text and controls), remove Material ripple/accessibility states, or run perpetual reflection animations. The adapted design uses static Compose gradient/border primitives.

Install `CoreMetrics-v0.8-analytics-only` and `CoreMetrics-v0.8-themes` APKs side by side; use only one recording overlay at once and import equivalent CSVs into both databases for comparison.

## v0.9.3 — HUD ownership, restart and export reliability (Portal test build)

**Development branch:** `fix/coremetrics-v093-hud-recorder-reliability`, stacked on v0.9.2 PR #24. No merge to `main`, no RC tag.

**Reason:** Odin 2 Portal Hades test (13 November 2026) showed two independently interactive HUDs (one READY, one STOP), failed Quick Settings recovery and many historical Android LOW_MEMORY exits. The source does not prove whether duplication was a same-build orphan window or concurrent v0.8/v0.9 test packages. Both cases require precautions.

**Changes:** A single `RecorderHud` owner is shared across Android Service objects inside the same `:monitor` process; WindowManager detach errors retain view handles rather than silently abandoning them; the HUD refuses a second attachment if a previous handle cannot be removed. RecordingService journals package/PID/HUD view identities, removal results and restart events. Quick Settings reports UNKNOWN or RECOVERABLE instead of assuming IDLE on provider failure; RECOVER validates the existing session lease and never starts an unrelated recording. Fresh START requests are also redirected to a resumable existing session. The app foreground handshake is rechecked asynchronously on Activity restart to hide its own HUD promptly. Both tiles retain separate jobs (REC vs HUD visibility).

**Integrity/UX:** Session report CSV/TXT, segment TXT/CSV and A/B text export intents are saved over Activity recreation, just like PNG; very short sessions are labeled INSUFFICIENT FOR COMPARISON. The A/B PNG uses A cyan/B orange like the UI; report footers and headers say v0.9.3. Compare selection displays graphics-driver metadata and warns that `Stock` denotes CPU tuning, not the Vulkan graphics driver.

**Test artifacts:** `CoreMetrics-v0.9.3-themes` (`app-v093themes.apk`, package suffix `.v093themes`) and `CoreMetrics-v0.9.3-frosted` (`app-v093base.apk`, package suffix `.v093base`). Test builds are intentionally separate local databases and Android packages. **Do not enable overlays or QS tiles for multiple variants together**; each installed package may legally display its own HUD, and a process-local registry cannot stop another app's overlays.

**Release:** JVM tests and GitHub Actions build are a compile/unit gate only, not proof of Odin LMK recovery or single-window behavior. See `docs/PORTAL_V093_ACCEPTANCE.md`. Keep main stable until controlled Portal testing and production signing gates pass.

## v0.9.2 — Analysis chart performance & lifecycle hardening (device-test draft)

**Branch:** fix/coremetrics-v092-performance-lifecycle, stacked on v0.9.1 audit/recording integrity branch. v0.9.1 and older branches are untouched.

**Display-only reduction:** 10,000 original points can be represented by at most four real extrema/endpoints per horizontal pixel column (first, min, max, last) plus explicit gap break markers. **Original samples, P50/P95, timestamps, session metadata, CSV/TXT/PNG exports, battery integration and A/B signs are unchanged.** The Classic two-track timeline, Advanced Diagnostic Explorer raw/rolling traces, and A/B cursor chart now prepare cached geometry at screen width rather than visiting every source sample for every cursor drag. P95/peak markers still use real source observations.

**Cursor precision:** Advanced Diagnostic Explorer uses binary search over source readings with a small near-endpoint tolerance and refuses a cursor deep inside a missing sensor/recording interval. The existing A/B/Classic \`CompareTimeCursor\` already uses binary search and was not needlessly replaced. No synthesized readings.

**Activity lifecycle/idle overhead:** Activity visibility no longer synchronously queries an Android ContentProvider hosted in the separate monitor process from \`onStart()/onStop()\`. It uses last-known active status, sends visibility commands only for user-started monitor state, and re-synchronizes on the first status poll. Idle screen polling backs off from 1.3s to 4.5s; active recording/standby still polls at 1.3s. The Activity closes its dedicated read-side \`SQLiteOpenHelper\` when destroyed; recorder's own WAL database connection is independent.

**Android test scaffolding:** An \`androidTest\` suite validates Android PNG decoding for all four visual finishes, the MIME/document contract, and Activity recreation. CI compiles \`assembleDebugAndroidTest\` but does **not** execute device/emulator tests. For execution, use \`gradle :app:connectedDebugAndroidTest\` on a clean test device/emulator, with no ongoing valuable recording.

**Builds:** \`CoreMetrics-v0.9.2-frosted\` / \`app-v092base.apk\` and \`CoreMetrics-v0.9.2-themes\` / \`app-v092themes.apk\`. Package IDs and local session databases are isolated from v0.9.1. They are debug device-test APKs, not release-signed Play files.

**Verification:** \`docs/PORTAL_V092_ACCEPTANCE.md\` and \`docs/V092_PERFORMANCE_AUDIT.md\`. Real-world frame-time, allocated memory, battery and LMK recovery have **not** been measured by source review or JVM tests; compare the same recordings against v0.9.1 before claiming gains. No RC1 tag or merge.

## v0.9.1 — Recorder and measurement integrity stabilization

**Branch:** `fix/coremetrics-v091-integrity-stabilization`, stacked on v0.9.0 PNG draft. This is a focused corrective release **without new features**, retaining two isolated v0.9.1 test APKs: Frosted (`app-v091base.apk`) and Themes (`app-v091themes.apk`).

- System-driven service destruction is no longer mislabeled a user-approved COMPLETED recording; explicit STOP/DISARM remain distinct.
- Tied core loads preserve a numeric maximum while leaving the ambiguous winning core unnamed. Recordings already missing historical values cannot be repaired without source evidence.
- CSV import is strict about duplicate/out-of-order timestamps and non-finite/implausible measurements, with atomic rollback rather than silent corruption. The legacy benchmark now validates CPU/GPU/load similarly to advanced diagnostics and scopes charging-status checks to the selected analysis interval.
- Observed CPU frequency-ceiling regimes split at real recording gaps and compute time-weighted discharge without bridging missing or filtered samples.
- Analysis errors show descriptive messages instead of unending spinners. Session Analyze scrolls to the top. A/B analysis warns when sampling cadence differs substantially.
- Standby HUD newline, TXT numerical precision/missing-value formatting, PNG °C labels, descriptive time bounds and thermal-median terminology are corrected.
- Pending SAF PNG export state is saveable across Activity recreation; failed writes attempt to remove only the just-created document.
- Existing Quick Settings Record and HUD tiles remain independent; neither is silently disabled. No app storage migration, no root privileges, no extra sampling.
- See `docs/V091_AUDIT_ROLLOUT.md` for priorities/deferred work, and `docs/PORTAL_V091_ACCEPTANCE.md` for device verification. This is not v1.0.0-rc1.

## v0.9.0 — Offline shareable PNG diagnostic scorecards

**Branch:** feature/coremetrics-v090-png-scorecards, stacked on v0.8.9 draft PR #21.

**Full session / custom segment:** Sessions → Analyze → Full recording or select range → Save PNG scorecard / Save & share PNG. Existing source data and analysis are reused; no new samples, no storage mutation.

**A/B selection:** Compare → choose shared elapsed window → Save PNG scorecard / Save & share PNG. Always B−A, with evidence caution labels. Same original-relative elapsed time for both sessions; no scene or FPS equivalence inference.

**Renderer:** User chooses Android Storage Access Framework document URI; app renders offline PNG on Dispatchers.IO (1600×1050 ARGB bitmap, ~6.7 MB, recycled after use). Standard Android ACTION_SEND share sheet, temporary read grant. No cache, external network, extra permission, screenshot of game or ongoing GPU effect.

**Content:** Source/session IDs, actual selected interval, CPU/GPU P50/P95 temperatures, CPU/GPU load, time-weighted battery net discharge, quality/coverage, time-aligned source sensor traces, and clear provenance and validity cautions. Gaps and unavailable readings never become artificial data. The scorecard makes no unsupported FPS, throttling or performance-winner claims.

**Builds:** CoreMetrics-v0.9.0-frosted (app-v090base.apk, com.locutus5382.coremetrics.v090base) and CoreMetrics-v0.9.0-themes (app-v090themes.apk, com.locutus5382.coremetrics.v090themes). These isolated test/debug APKs are NOT Google Play signed release artifacts.

**Acceptance:** docs/PORTAL_V090_ACCEPTANCE.md and docs/RC1_READINESS_PLAN.md.
## v0.8.9 — Four-finish Appearance Studio (experimental)

**Branch:** feature/coremetrics-v089-appearance-studio, stacked on v0.8.8.1 recorder recovery/HUD fixes (draft PR #20). Prior branches and main are unchanged.

**Two isolated APK variants:** CoreMetrics-v0.8.9-frosted (app-v089base.apk, package com.locutus5382.coremetrics.v089base) is locked to the original Frosted Glass. CoreMetrics-v0.8.9-themes (app-v089themes.apk, package com.locutus5382.coremetrics.v089themes) lets users select four appearances; it is a separate app ID with isolated SQLite sessions and preferences. Use CSV import/export to move recordings.

**Four finishes, stable preference keys:**
- **Frosted Glass:** preserve original Cobalt & Solar translucent background and card styling, native control shapes and telemetry.
- **True Glass** (existing preference key crystal_aero): layered optically clear panes, bright refractive upper rims and cobalt/cyan/orange backdrop ribbons. This is a **visual approximation**, not live blur/refraction of game content.
- **Modern Colorful** (existing key solar_pulse): saturated cobalt and orange high-contrast controls, bold cyan/yellow instrument accents, colorful slider tracks and highlighted KPI tiles.
- **Technical Cockpit** (existing key cobalt_matrix): compact sharp-corner matte navy surfaces, instrument panel rulers, tight outlines, monospace metric/navigation identity and cyan/solar guidance.

**Interaction and navigation:** Settings has a static four-card preview gallery with one-touch apply; THEME/STYLE quick-cycling in the header is still available in the themes variant. Controller-friendly single-click tile surfaces, theme-aware range sliders, and one-shot sweep/glint on existing CoreMetrics action pills and navigation tiles. No continuous animation, no image assets, no GPU shader blur, no web content.

**Scope:** UI-only Compose changes. In-game native floating HUD, recorder service, LMK recovery, permissions, SQLite, telemetry cadence, CPU/GPU sensor collection, FPS caveats, A/B math, CSV/TXT exports and analytics engine are deliberately unchanged. The new finishes are evaluated with the v0.8.9 Frosted baseline; all values across variants must agree when fed the same source CSV.

**Validation:** See docs/PORTAL_V089_ACCEPTANCE.md. This branch is an Android-build + device-test candidate, not a visually approved public release. If any alternative degrades readability or performance, we can remove that alternative without touching the recorder.

## v0.8.8.1 — Recorder Recovery and HUD Reliability (Portal test)

**Branch:** fix/coremetrics-v0881-recorder-recovery-hud, stacked on v0.8.8 draft PR #19.

**Reason:** Odin 2 Portal logs on November 13 show Android LOW_MEMORY termination of the :monitor foreground process after HUD_LOCK. The old HUD_LOCK, HUD_UNLOCK, HUD_CONFIG and HUD_SHOW/HIDE handlers returned START_NOT_STICKY even during active recording. Android's service continuation policy could therefore drop the service rather than request its restart.

**Fixes:** Active REC/standby and non-terminal HUD commands preserve START_STICKY. Explicit STOP/DISARM still clear the lease and prevent restarting. The bounded recent-lease + SAME_SESSION_ID + limited retry recovery policy remains; a killed process cannot record during downtime and restart is never guaranteed by OEM Android. The locked-HUD user preference is saved with the lease and restored on eligible recovery, including the separate 48dp touchable unlock chip.

The HUD shows enabled metrics across short, legible rows rather than truncating GPU/RAM. The floating HUD temporarily hides while CoreMetrics' own Activity is foreground and returns to the game unless the user explicitly hid it. This pause does not alter user HUD preferences or restart lease and does not stop REC.

Recorder diagnostics now use the actual installed package version. Thermal trends no longer imply equilibrium or soak time; nominal battery-capacity runtime is behind a low-confidence disclosure, and sample coverage is labeled correctly.

**APK variants:** CoreMetrics-v0.8.8.1-frosted (app-v0881base.apk; package com.locutus5382.coremetrics.v0881base) and CoreMetrics-v0.8.8.1-themes (app-v0881themes.apk; package com.locutus5382.coremetrics.v0881themes). Separate recordings and app state. Import old CSV files as needed.

**No changes:** A/B statistical analysis, HUD sample source, polling cadence, privileges, database schema, recorder stop semantics, event markers or theme visuals.

**Device tests:** See docs/PORTAL_V0881_ACCEPTANCE.md. Next v0.8.9 theme designs are mockup-only until approved.

## v0.8.8 — A/B Diagnostic Comparison Studio (device-test draft)

**Development:** feature/coremetrics-v088-ab-comparison-studio stacks on v0.8.7 draft PR #18, without changing earlier branches or main.

**Two independent test builds:** CoreMetrics-v0.8.8-frosted, package com.locutus5382.coremetrics.v088base (app-v088base.apk), and CoreMetrics-v0.8.8-themes, package com.locutus5382.coremetrics.v088themes (app-v088themes.apk). Frosted stays locked to the familiar original visual style, themes APK retains existing optional finishes. Separate databases and preferences; transfer prior runs via CSV.

**Compare A/B Scorecard:** Compact single-card comparison of CPU/GPU temperature medians and P95, time-weighted verified discharge W, median CPU/GPU loads, and observed P3/P7 cap groups; every difference is strictly **B − A**. Original game notes, status, drivers, rendering scale, temperature starting points, power provenance, load differences, gaps, and sampling coverage feed **transparent evidence warnings**, not a single winner score. "CONDITIONS UNVERIFIED" is not proof of equivalent scenes.

**Synchronized elapsed-time crop:** Default shared overlap, optional skip-first-30-seconds, custom Material 3 RangeSlider, controller-friendly 5s adjustments. All A/B statistics and report exports use the same shared selected elapsed time measured **from each original recording's first sample**, never the first post-filter point. Show a dimmed range on the existing comparison chart with separate chart cursor. Optional full-recording display shows unequal tails for visual context only; unmatched tails NEVER contribute to A/B deltas. The existing GPU >=15% heuristic filters chart traces ONLY and cannot bias scorecard statistics.

**Export selected A/B report:** New independent SAF TXT export with source session IDs and original status, metadata, selected elapsed-time bounds, sample timestamps/count, A/B medians and P95, power coverage and time-weighted discharge, frequency ceilings and explicit comparability limitations; append existing detailed report for each cropped run. Require at least 6 recorded samples spanning 20 seconds per run; prohibit exports from partly loaded >12k-sample histories. No CSV schema changes or database migration.

**Scope intentionally excluded:** no game-time sampling changes, no privileged FPS claim, no causal profile verdict, no event markers, no automated findings, no theme redesign or PNG share card.

**Odin acceptance:** docs/PORTAL_V088_ACCEPTANCE.md. Validate on actual handheld before merge.

## v0.8.7 — Single-Session Analysis Studio (device-test draft)

**Branch:** feature/coremetrics-v087-single-session-studio, stacked on v0.8.6 draft PR #17. v0.8.6 remains unchanged.

**APK variants:** CoreMetrics-v0.8.7-frosted (app-v087base.apk, package com.locutus5382.coremetrics.v087base) and CoreMetrics-v0.8.7-themes (app-v087themes.apk, package com.locutus5382.coremetrics.v087themes). Separate preferences, recording database, provider authorities, tile labels and app IDs. Frosted variant forces the existing original Frosted Glass appearance.

**Single-session UI:** Full recording / Skip first 30s / Custom range, one accessible Material 3 RangeSlider and independent 5s start/end step controls for D-pad/touch usability, plus a source CPU temperature overview with dimmed excluded regions. The in-graph scrub cursor and range-selection thumbs are separate controls. Original timestamp origin does not reset when cropping. Session overview, medians, P95, peaks, 60s medians, charts, energy and technical evidence all use the SAME selected samples; the full source remains unchanged.

**Offline calculations:** Time-weighted percentage of valid contiguous observed duration where both adjacent readings fall within ±2°C for CPU/GPU temperatures, ±15% of the median for discharge-only watts, or ±10 percentage points for CPU/GPU load. Mean absolute adjacent step delta provides volatility in signal units **per recorded sample step**; sampling cadence is shown, so users cannot confuse this with per-second volatility. A stability percentage is withheld with insufficient paired time (<30s) or <4 valid pairs. No thermal soak/equilibrium detector or invented FPS.

**Selected-segment exports:** Two explicit SAF actions: TXT report (source ID, source profile, original timeline bounds, selected observations, BenchmarkAnalyzer + SessionDiagnosticsAnalyzer findings + stability/volatility caveats) and schema-compatible CSV (only real samples inside inclusive selected timestamps; retains all existing columns, times, driver/scale and original source session ID in CSV rows). No unobserved boundary interpolation or database migration. Export disabled if fewer than 6 samples spanning 20s or a session exceeded the first 12,000 loaded points. Neither export alters the original recording. CSV files can be imported as separate sessions.

**Scope exclusions:** A/B UI and report selection deferred to v0.8.8; no automatic executive findings, event markers, HUD changes, theme redesign, or shareable PNG.

**Acceptance checks:** docs/PORTAL_V087_ACCEPTANCE.md. Release to users only after first physical Odin 2 Portal session validation and comparison against raw CSV.

## v0.8.6 — Advanced single-session diagnostics (draft)

**Stacked development:** `feature/coremetrics-v086-session-diagnostics` targets the v0.8.5 branch; draft PR #17. v0.8.5 remains unchanged.

**APK artifacts:** `CoreMetrics-v0.8.6-frosted` (`app-v086base.apk`, package `com.locutus5382.coremetrics.v086base`) and `CoreMetrics-v0.8.6-themes` (`app-v086themes.apk`, package `com.locutus5382.coremetrics.v086themes`). The former is fixed Frosted Glass; the latter keeps the prior optional themes. Both are separately installed with isolated app-private databases and preferences.

**Sessions → Analyze** now includes:
- Median (P50), 95th-percentile (P95), peak with absolute sample timestamp, and final 60-second rolling median for CPU/GPU/battery temperatures, CPU/GPU load, busiest observed core and discharging battery wattage.
- Synchronized thermal, CPU-clock/CPU-temperature, core-load, GPU-load/GPU-clock and discharge/battery-level timelines. Curves are drawn only over contiguous valid recorded samples; the 60-second rolling median resets across gaps. Orange P95 thresholds, upper-tail dots and red peak diamonds are indicators, not causal diagnoses.
- Exposure to sustained >=85% GPU and busiest-core utilization, including longest observed high-load period; changes in **observed CPU frequency ceilings**, distinct from momentary `scaling_cur_freq`; first-three-minute and last-minute CPU temperature regression slopes.
- Indicative remaining/full-charge runtime only if the session was exclusively discharging with >=120 seconds and >=60% valid discharge coverage. Uses measured time-weighted battery-terminal watts with nominal 30.8 Wh usable energy as an assumption, **not** a battery-health measurement or verified prediction.
- Source integrity, valid reading counts, gaps, and explicit limitations. The original selectable 2-track view including RAM remains under expanded technical diagnostics. Single-session plain-text report export includes the new analysis; A/B compare is unchanged.

**Implementation:** Existing SQLite data already contains battery temperature/percentage and CPU/GPU clocks, but the points query previously omitted them. No database migration, new permissions, thermal/root writes, new sample loop or additional runtime overhead during gameplay. More context: `docs/PORTAL_V086_ACCEPTANCE.md`.

## v0.8.5 — HUD visibility, richer analysis, Frosted + optional themes

**Two isolated debug test APKs built from identical v0.8.5 recording/analytics source:**
- **Frosted**: `CoreMetrics-v0.8.5-frosted` → `app-v085base.apk` (`com.locutus5382.coremetrics.v085base`). Original Cobalt & Solar Frosted Glass only, with one-shot button light sweep on main actions.
- **Themes**: `CoreMetrics-v0.8.5-themes` → `app-v085themes.apk` (`com.locutus5382.coremetrics.v085themes`). Frosted default plus Crystal Aero (transparent static ribbons), Solar Pulse (bold solid controls) and Cobalt Matrix (techy grid/ticked gauges). UI-only; the in-game WindowManager HUD is unchanged by app themes. The experimental optical glass *simulates* depth; no true live background refraction, blur or permanently animated shaders.

**The changes shared by both builds:**
- **CM HUD Quick Settings tile** now shows/hides the actual overlay without terminating any active recording. ON = visible; HIDDEN REC = recording active, HUD hidden; OFF = no visible HUD. Hiding idle standby releases the idle foreground service. The separate `CM REC` tile remains as an emergency fallback during device validation. Notification exposes Restore HUD while hidden and REC is active. The two-window independent touch-lock remains intact.
- **Explicit Dock** control minimizes the overlay to the existing edge tab with a larger touch target. Capsule and rounded-card HUD layouts share the same lock/restore behavior and editable opacity/size. HUD mini-graphs and their toggle have been removed (they were too small and unavailable in normal pill mode).
- **Adaptive Metric Colors** are opt-in under HUD Studio and operate only on *recorded* snapshots: CPU/GPU temp 65°C elevated yellow, 80°C high red; utilization ≥90% elevated yellow, never red just for high load; RAM assessed from Android `ActivityManager.MemoryInfo` low-memory threshold (not a fixed % of RAM used). Thermal color changes use a small release margin to avoid flickering. These are UI heuristics, NOT verified silicon/battery safety limits or automatic throttling triggers.
- **Single session** begins with meaningful temperature/power/duration and data-integrity summaries plus two synchronized, selectable metric tracks (CPU/GPU temperatures, CPU/GPU loads, discharge-only battery watts, RAM). Actual samples only; no interpolation through missing readings or time gaps. Warm-up elapsed labels remain tied to original recorded timestamps. Historical technical evidence remains behind a disclosure.
- **Compare** starts with Session A/Session B columns, compact differences, telemetry-integrity versus benchmark-comparability warnings, then the A/B chart using the overlap window by default. A switch can show complete, unequal-duration runs. Scroll-heavy CPU-regime cards and raw evidence are collapsed by default. No claim of synchronized gameplay scenes or comparative FPS without verified FPS data.
- **Live idle** shows setup guidance and a last-recording shortcut instead of four blank gauges; no idle hardware sampling task added.
- **Glass press glint** is interaction-driven (touch/gamepad focus), ~220ms with modest scale feedback, never an infinite background animation. Frosted surfaces and existing brand colors are preserved.

**Engineering constraints:** This is not a fix for Android `LOW_MEMORY` terminations while Eden starts, nor a production release. No new permissions, CPU/GPU writes, root/PServer, tracking SDKs, OOM-priority manipulation, database migration or sensor sampling threads. User should still launch Eden before arming HUD via Quick Settings. Build variants have *separate* SQLite databases: do not delete older versions before exporting needed CSVs; never run two overlay variants simultaneously.

See `docs/PORTAL_V085_ACCEPTANCE.md` for physical device acceptance and data integrity checks.

## v0.8.4 — landscape polish and controller navigation (draft)

This iteration follows real Portal v0.8.2 tests, Gemini's visual mockups, and the v0.8.3 UI fixes. Preserves v0.8.3's safe native HUD lock, offline recorder and actual samples. The existing separate v0.8.3 APKs remain available.

**What changed**
- **Quick native drawer 2×4**: CPU T, GPU T, CPU %, GPU % / RAM, W, Graphs, REC or STOP; plus a smaller glass opacity footer. Warm Solar Orange and Cobalt ON chips have ✓ and bright borders; OFF chips are muted/borderless. The panel remains width-bounded and its WindowManager X coordinate clamps after opening. The compact pill ellipsizes rather than wrapping into two lines; Pro layout can still wrap. Do **not** blindly enlarge text to 460dp because that pushes the 48dp unlock target offscreen. Independent unlock chip keeps its 48dp touch area while its painted glass is shorter. Frames and sensor cadence are unchanged.
- **Never fake FPS**: no dead FPS toggle in native quick drawer or main HUD Studio; even if a previous saved preference requests FPS, the pill omits it until a real non-null source is measured. Unverified layer FPS remains disabled.
- **Controller**: D-pad and hat axes use actual focus; right-stick vertical Z/RZ mapping scrolls the visible page; on Compare, horizontal Z adjusts the *same* normalized cursor used by touchscreen and focused Slider. A remains a center/activate key. Uses standard Android hardware axis mapping as a starting point; Odin firmware behavior remains unverified. Stick actions are only processed while the UI activity is visible.
- **Widescreen app**: two-column Core 0–3 / Core 4–7 busiest-core chart; analysis energy and observed-profile panels already split in v0.8.3. Session History rows fill the width and anchor compact export, analyze, tags, report controls on the right. Live header has an ARM HUD control and Live rings incorporate CPU/GPU load, battery status and Android-reported RAM total as subtitles, allowing redundant metric rows to disappear. Critical measurement cautions stay with reports, while full provenance is in Settings/documentation.
- **Appearance**: normal `CoreMetrics-v0.8.4-standard-debug` is the **unified** app with Frosted, Liquid and Tactical and persistent top-bar THEME button. The explicit `CoreMetrics-v0.8.4-analytics-only` remains fixed Frosted as a regression reference. The `CoreMetrics-v0.8.4-themes` isolated test build remains themed. All three use the same source code and separate Android application IDs; never run multiple CoreMetrics HUDs simultaneously. The in-game native WindowManager HUD does not inherit Compose app-screen finishes.
- **Optional immersive view**: Settings can hide status/navigation bars using Android `WindowInsetsController` transient swipe-reveal behavior. **Off by default**, reversible and only for the app's main Activity. Android's system navigation always remains recoverable. It is not a root/overlay change.

**Intentionally not included**
- No idle Live sampler loop: this version keeps the original user-started session sampler design. Four Live gauges can remain unavailable when idle and standby performs no sensor reads. An optional lifecycle-scoped read-only preview could be evaluated separately after Portal memory tests, without writing SQLite.
- No unconditional `Activity.finish()` on arming HUD: it doesn't guarantee the process is removed from RAM or prevent LMK kills and can unexpectedly close the configuration screen. Eden-first → Quick Settings HUD/REC remains the tested route.
- No automatic immersive mode, privileged FPS/PServer, performance/thermal controls, OOM changes, or claims that Mario Kart's FPS is measured by CoreMetrics.

**Evidence limits**: Four sessions `COMPLETED` in the user-supplied v0.8.2 recorder report show that those sessions saved normally, but **not** that no sample was ever dropped or that the whole emulator launch was stable. Five `LOW_MEMORY` exit-history entries occurred near Eden startup. The 5.3–5.5 GB app-RAM overlay and LMK exits are correlated, not evidence of a precisely measured threshold. The `98%` CPU0 winner share is from an older recording and might include tie bias. Older SQLite data are deliberately not reclassified.

**Release gate**: the final commit requires successful GitHub Actions unit tests and all three APK builds, plus physical Portal tests in `docs/PORTAL_V084_UI_TEST.md`. All stacked PRs remain draft; do not publish to Play or merge simply because CI passes.

## v0.8.3 Portal findings and UI correction

Real-device evidence dated 2026-11-13: Odin 2 Portal reports multiple Android `ApplicationExitInfo.LOW_MEMORY` exits for BOTH the user interface and the `:monitor` process during Eden launch. Selected exits had monitor lastPssMiB 63 and UI 116; their 0 PSS samples mean 'not measured', not zero usage. An independent frame overlay showed Eden process RAM around 5.3–5.5 GB. CoreMetrics cannot override OEM memory pressure. **Recommended workflow:** launch the emulator and load the game, then use Android Quick Settings' **CM HUD** tile and tap REC; or use the independent CM REC tile. No automatic monitoring while standby.

The supplied recorder journal shows successful lock/unlock and four `COMPLETED` sessions (20, 44, 3, 6 samples). Lock chip behavior is preserved.

**Changes in both v0.8.3 builds**
- Fix the drawer over-expanding the overlay: cap total HUD width, give the live telemetry label a weight so the settings/REC/lock controls stay on-screen, and make both drawer-control rows horizontally scrollable *within* the HUD.
- Clear ON/OFF state: cyan frosted switches with a dot when enabled, nearly borderless muted controls when off, red STOP for active recording, live opacity percent, and a raised settings/close-drawer indicator. Native HUD transparency settings remain editable; theme is a separate app-screen finish.
- D-pad fix: request initial focus on the actual Live button instead of a focus-stealing root container; show gold focus outline and support physical D-pad keys, hat axes, right-stick Z/RZ or RX/RY, L1/R1 and L2/R2 for tabs, gamepad A for activation, and native focused slider handling. The Portal must be in controller/joystick (not mouse mapping) mode. Actual controller support remains an on-device acceptance requirement.
- Discoverable themes: the **Themes APK** shows a visible `THEME · Frosted Glass ↻` control on the top bar of EVERY page, cycling to Liquid and Tactical. Settings includes direct choices and CURRENT feedback. The **Analytics-only APK** intentionally stays original Frosted and labels itself `FROSTED · FIXED`. These finishes change application screens only, **not** the native floating HUD.
- Session and Compare panels use a balanced side-by-side landscape layout for Energy and Selected-vs-Observed, rather than a narrow single-column stack leaving the right half blank.
- Address misleading CPU0 winner artifacts: tied or zero-load per-core samples now produce **no unique busiest-core identifier** rather than automatically picking the first `cpu0`. Existing SQLite samples are never rewritten; the correction applies only to new data. Busiest-core bars are not CPU residency.

**v0.8.3 packages** (separate databases, installable alongside older releases):
- `CoreMetrics-v0.8.3-analytics-only` / `app-v083base.apk` / `com.locutus5382.coremetrics.v083base`.
- `CoreMetrics-v0.8.3-themes` / `app-v083themes.apk` / `com.locutus5382.coremetrics.v083themes`.

No root, RAM-killer priority edits, extra sensor polling, fabricated FPS, changes to battery/CPU caps or automatic launch on boot. This is **not** a proven fix for low-memory terminations. See `docs/PORTAL_V083_UI_CONTROLLER_TEST.md`.

## v0.8.2 efficiency and consistency review

Gemini's v0.8.1 follow-up audit was checked against the repository source. All eight ideas are directionally valid, with two safeguards: do not permanently cache an empty CPU policy list, and do not reset elapsed-time labels when analysis drops the first 30 seconds.

- **One sticky battery broadcast per sample** feeds battery percent/temperature and net electrical power status. The standalone BatteryPowerReader.read() API remains supported.
- **CPU policy directory discovery and compiled regular expressions are reused.** The policy path cache retries if its initial scan is empty; current limits and clocks are still read from sysfs every sample. Changes to policy *directory topology* after successful cache creation may require a fresh recorder.
- **HUD blink** is scheduled only for a visible expanded recording pill when blinking is enabled; dock, option changes and mode transitions cancel/re-arm callbacks centrally. The dock's existing drawable has its color changed without constructing a new one on each refresh.
- **Compose memoization** covers the observed-profile card, busiest-core chart, linked timeline, A/B active-gameplay filters and A/B numeric summaries, invalidating when inputs change. No extra polling.
- **One CPU ceiling review rule** now feeds the Live warning, persisted-profile card and benchmark quality check, including Medium/Large UC vs Stock and generic Underclocking with Stock-like CPU ceilings. CPU ceilings cannot verify tuner identity or rule out GPU-only changes.
- **30-second warm-up** now applies consistently to Session detail and Compare profile/core/regime/thermal views; elapsed labels retain the original recording origin. Manual linked source timelines and original stored samples remain complete.
- **A/B Δ battery W** now uses only observed discharging samples, not charging or missing readings. It is labeled as a **sample mean**, distinct from integrated Wh, normalized mWh/min and time-weighted W.
- Unused CompareChart/InstrumentBackdrop visualizations and scrubFraction state are removed. No database migration, extra permissions, root, privileged PServer, FPS fabrication, driver manipulation or OOM priority hack.

Two separate packages (data does **not** sync automatically):
- `CoreMetrics-v0.8.2-analytics-only` / `app-v082base.apk` / `com.locutus5382.coremetrics.v082base` — original Frosted Glass always.
- `CoreMetrics-v0.8.2-themes` / `app-v082themes.apk` / `com.locutus5382.coremetrics.v082themes` — Frosted default, optional Liquid Glass / Tactical Flat.

See `docs/V082_PROFESSIONAL_AUDIT.md` and `docs/PORTAL_V082_TEST_PLAN.md`. Source review and a green CI build do **not** establish device memory survival, real game FPS accuracy, overlay touch success, real performance improvement or Play Store readiness.

## v0.8.1 Gemini-review fixes — separate Odin validation builds

Draft fix branch stacked on `feature/coremetrics-v08-practical-refinements`. No changes to sampling, source CSV contents, native HUD style, user-selected Frosted Glass default, energy integration, GPU driver control, permissions or recovery policies.

1. **Warm-up really applies to CPU regime cards:** optional `Skip First 30s` now passes the same trimmed Session A window to the ceiling regime analyzer as the A/B charts. Source timestamps remain absolute and elapsed labels refer to the original first sample. The untrimmed linked session timeline and source rows remain intact.
2. **Lock closes settings drawer safely:** when a touchable independent 48dp unlock chip is attached, the HUD main window becomes click-through and the expanded drawer is hidden. Lock failures restore the previous window flags, alpha, lock state and drawer state; the notification unlock fallback is unchanged.
3. **No two perpetually unavailable Live gauges:** the fourth ring now shows observed **RAM used (GiB)**, scaled by `ActivityManager.MemoryInfo.totalMem` queried once for display, not by a fabricated 7.02GiB constant. The fourth metric card now shows Android's explicit **Battery status** or Unknown. Unverified layer FPS and frame time remain absent rather than pretending they were measured.
4. **Bidirectional label review:** selected `Underclocking` combined with only stock-like observed P3/P7 ceilings is marked as a possible CPU mismatch in profile and benchmark analysis. This **does not prove the tuner failed**: GPU-only or other underclocking may be active. Stock selected plus observed Medium/Large UC remains flagged. Diagnostic report heading updated to v0.8.1.
5. Two separately installable and distinct-storage APK variants:
   - `CoreMetrics-v0.8.1-analytics-only` → `app-v081base.apk` / `com.locutus5382.coremetrics.v081base`, original Frosted Glass;
   - `CoreMetrics-v0.8.1-themes` → `app-v081themes.apk` / `com.locutus5382.coremetrics.v081themes`, Frosted by default with optional Liquid/Tactical app-screen finishes.
   The v0.8 packages are not overwritten. Imported CSVs do not automatically synchronize across package databases.

Acceptance: See `docs/PORTAL_V081_REGRESSION_TEST.md`. Successful **final commit** CI and direct Portal tests (lock/overlay, RAM capacity, warm-up, both theme variants) are required before merging or publishing. Keep the PR draft until physical verification.

## v0.8 practical add-ons (two side-by-side installable apps)

**Both variants use one shared telemetry, SQLite and analysis codebase.** `v08base` always preserves the original **Frosted Glass** styling. `v08themes` defaults to Frosted, but may opt into static-gradient **Liquid Glass** or opaque **Tactical Flat** from Settings. The experimental finishes affect app screens only, never the native in-game HUD. No runtime blur of text, additional telemetry sampler, background animation or root.

- **Manual session tags:** In Sessions, expand **Tags** for one-tap `System` / `Turnip` / `Custom: ...` driver and `Handheld 1x` / `Docked` / `Custom: ...` resolution/scale; `None` means unknown. This is a compact optional editor, not automatic driver detection. Tags persist in SQLite schema v4 and two appended CSV columns (28 total; the 26 original columns remain intact). v1→v4, v2→v4 and v3→v4 migration retain old samples. A/B text reports contain tags and warnings for unknown/different driver or resolution.
- **Power conditions:** Session History distinguishes `PLUGGED IN` and `MIXED POWER` from explicit Android battery-status evidence. Unknown charging state does not imply plugged. Energy cards and reports disclose power condition; Wh, mWh/min and time-weighted W integrate **discharging intervals only** and omit charging/bypass/missing readings. A/B warns when conditions differ.
- **Warm-up:** Analysis/Compare switch **Skip first 30s (Warm-Up)** excludes the first 30 seconds *after the first recorded sample*, not necessarily game-world load completion. This changes analysis and filtered A/B charts and exported A/B reports, but never changes source SQLite rows, raw CSV export, manual linked timestamps or unfiltered CPU-ceiling phases.
- **History:** Optional **Hide runs under 15s** filter; no deletions, no hiding from Compare/CSV exports.
- **Controller:** Shoulder L1/R1 navigation handled at Activity key dispatch even after touchscreen focus; D-pad moves between focusable session chips and adjusts a focused A/B scrub Slider. Android's focus management can differ on Portal firmware; this needs physical test on all five tabs (Live, Sessions, Compare, HUD, Settings).
- **Quick Settings:** Separate `CM HUD` standby tile with an outlined floating-panel/telemetry icon. It arms/closes *standby only*, refuses to terminate a running recording, and never samples sensors alone. Existing `CM REC` tile stays dedicated to start/stop recording. The HUD Studio offers **Add HUD tile** and **Add REC tile**. Both variants have separate tile instances/providers.
- No change to recording sampling cadence, PServer privilege, OOM scores, original Frosted styling or the new independent vector unlock chip.

**Developer test gates:** Run `docs/PORTAL_V08_PRACTICAL_TEST_PLAN.md`; CI passing alone is not proof of controller navigation, overlay stability or experimental themes on the Odin.

## v0.8 analytics-only (default Frosted Glass UI is unchanged)

Four targeted improvements, no hardware control or sensor polling:

1. **Manual, reversible session links.** New SQLite schema v3 adds an association table only; original session IDs, timestamps, status, samples and CSV stay independent. Compare links/unlinks any selected pair, and shows a visual actual-time gap. It never silently stitches samples or assumes an LMK recovery.
2. **Observed cap phases.** Show user-selected configuration and all observed cpufreq P3/P7 phases separately; a cap is not proof of which tool applied it. Missing measurements remain unavailable.
3. **Busiest Core Distribution.** Eight winner-frequency bars drawn from the sampled /proc/stat busiest-core value, not a CPU time-in-state/residency chart. No additional hardware reads.
4. **Normalized energy.** Integrated battery-terminal Wh, corresponding mWh/min and time-weighted mean W, all computed from the same valid discharging time intervals; missing/charging/large gaps excluded. Report and Compare warn about different workloads, durations and coverage.
5. **Minor sensor provenance.** Settings explains thermal type discovery, KGSL clock paths, Android battery broadcast, /proc/meminfo and why constant frequency/temperature values do not imply a broken sensor.

**Session naming:** New recordings get an immutable numbered label `Session <ID>`, including imports. Session configuration choices are **Stock, Underclocking, Custom**; game/track is an optional separate note. Historical data and original configuration labels remain intact in SQLite/exports; the session list now shows numbered display names for consistency. Untrusted/unknown observed caps are never silently called Stock.

**Testing:** `CoreMetrics-v0.8-analytics-only` APK uses `com.locutus5382.coremetrics.v08base`, separate from existing v0.5/v0.6/v0.7 installations and databases; import your saved CSVs for comparisons. Do not simultaneously run two overlay recorders. This build retains the exact existing Pure Frosted Glass visual identity, including HUD.

**Separate experimental themes build:** A sibling v0.8 branch will add optional Liquid Glass and Tactical Flat finishes while keeping Frosted default. Theme choice is UI-only, does not alter the native WindowManager gameplay HUD, and requires real Odin landscape/accessibility testing.

## v0.7 draft — touchable lock, bounded memory recovery and benchmark evidence

- **Lock controls fixed:** The main telemetry HUD uses `FLAG_NOT_TOUCHABLE` at window alpha 0.70, with a **second 48dp, touchable 🔒 unlock chip** placed near its original icon. The chip is created *before* locking or the lock fails safe. It is removed on unlock/disarm; the notification and HUD Studio are separate fallbacks. Unlike a normal child of the locked HUD, this chip can accept touches independently.
- **Low-memory diagnosis:** The user-supplied recorder log shows Android `LOW_MEMORY` process kills for both UI and recorder. Fixed a misleading memory unit: Android's `ApplicationExitInfo.pss` is already in kB, so dividing by 1024 reports **MiB** (57–78 MiB at last measurement, not KiB). New diagnostics include a current system RAM snapshot.
- **Best-effort recovery:** A user-enabled foreground standby/recording now returns `START_STICKY`. On an Android-managed restart with a null intent, a private authenticated-by-app-state lease must be fresh (standby within 10 min, REC within 90 s of last heartbeat), from this boot, have at most two recovery attempts, and have a still-active SQLite session for REC. Recording continues under the SAME ID, preserving the original start time and leaving a genuine sample gap. STOP/DISARM explicitly clears the lease. The app **cannot prevent Android/AYN from killing it** and a restart is not guaranteed. No boot receiver, root, memory boosting, wake lock or concealed recording.
- **Import provenance correction:** Imported CoreMetrics CSV files retain their original completion outcome as `IMPORTED_COMPLETED`, `IMPORTED_INTERRUPTED_RECOVERED`, etc., rather than converting every source to the indistinguishable `IMPORTED`. Unknown/legacy imported source status is assessed conservatively.
- **Benchmark analytics:** (1) disclosed heuristic quality score with interrupted/gapped/short-session penalties, observed cap mismatch flags; (2) 60-second CPU thermal regression and conservative steady-state candidate; (3) integration of battery-net discharge watts over valid adjacent samples to estimate **Wh consumed** (no gap fill, charging or bypass); (4) FPS/W and joules/frame **unavailable** until independently validated game-frame timestamps (do not invent 1% lows); (5) exportable, offline text session and A/B evidence reports plus in-app cards.
- **Observed profile warning:** The uploaded Mario Kart recordings were manually labelled `Stock` but showed P3 1.79 GHz / P7 1.84 GHz, matching previously documented Large UC ceilings. A live label/cap warning and report flag identify this mismatch; measured ceilings do not prove which tuner applied them.
- A separate `CoreMetrics v0.7 Test` APK uses `com.locutus5382.coremetrics.v07test`, independent database, provider, Quick Settings tile and preferences. It can coexist with the older v0.6 Preview and v0.5. Import the user's CSVs into the Test app; **run only one HUD/recorder at a time**.

## v0.6 draft — CSV import, A/B chart scrubber, four HUD graphs, edge dock and FPS Lab

- Four per-metric bounded 30-sample sparklines in the floating Pro HUD: CPU %, GPU %, EMA CPU temp and available layer FPS. No separate sensor loop; standby samples nothing.
- Drag the HUD metric area near a bezel (within 16dp) to collapse it into a 12dp visible tab with 44dp touch target. Tap to expand. The in-game ⚙ drawer controls metric visibility, opacity, graph visibility and REC/STOP. Lock/unlock continues using the notification or Studio.
- Offline, atomic CSV import using the Android Storage Access Framework. Compatible CoreMetrics CSV exports become separate `IMPORTED` sessions with a 12,000-record cap, valid timestamps and headers, and no database migration. Partial/malformed imports roll back. `game_label`, `observed_cpu_limits` and power statuses are preserved.
- Elapsed-time-aligned A/B comparison with CPU/GPU temperatures and loads, battery-terminal power and (when measured) SurfaceFlinger layer FPS. Touch/drag on the graph or use its controller-friendly slider. Nearest point shown within ±10 s; no made-up values across gaps or beyond end.
- FPS Lab is a separately compiled, **not connected, not running** read-only interface/probe state machine with fake-host tests, handshake attestation requirements, unique SurfaceView selection, chosen presentation column, cooldown and two-failure circuit breaker. It does not include a PServer host launcher or privilege adapter. Activating this requires physical Odin read-only host verification and timestamp provenance before implementation.
- No additional Android permissions, no root escalation, frequency writes, user-supplied privileged shell commands, extra background recorder, 1 Hz SurfaceFlinger dumps or data transmission.

**Testing two versions:** Install the **v0.6 Preview** APK alongside the original v0.5 APK, which uses a different package name. They each keep their own sessions. Stop/disarm one HUD and its recorder before starting the other to avoid changing measurements and overlay-touch behavior. The regular v0.6 APK retains the original package ID and therefore is an upgrade rather than a parallel installation; debug signing differences may block an in-place upgrade.

## v0.5 draft — observed regimes, thermal trends, original icon, touch-lock

- Adds an original CoreMetrics telemetry-pulse adaptive icon, a themed monochrome icon, and proper notification/Quick Settings glyphs.
- The existing single sampling loop drives an EMA (alpha 0.35) CPU HUD value, a raw temperature peak, and a smoothed 15-second trend arrow. Raw SQLite/CSV values are unchanged.
- The **Compare** screen can inspect one previously recorded SQLite session for contiguous *observed* policy3/policy7 CPU ceiling phases, with 1–2 sample glitch debounce, stock/medium/large cap labels, conditional per-phase battery-terminal discharge averages, nominal battery projections (30.8 Wh assumption), and tinted time ranges. It retains A/B two-session comparison.
- HUD touch lock uses `FLAG_NOT_TOUCHABLE` with explicit window alpha 0.70 for Android 12+ pass-through rules. A locked overlay **cannot unlock itself**. Use its foreground-notification Unlock HUD action or the HUD Studio page; the QS tile keeps its REC/STOP role. Other overlapping overlays may still block pass-through.
- Safety: no added polling, no root/PServer/SF probes, no hardware-frequency modification, no invented FPS, no uploaded telemetry or additional permissions. Device validation and CI are required.
- Currently **no external CSV import**: the in-session analyzer reads the existing session SQLite database. If you only have an exported Hades CSV, import is a separate follow-up task.
- Advanced per-metric sparklines, edge-tab docking, a privileged read-only FPS provider, 1% low FPS and verified frame pacing remain separate test-gated tasks. Do not equate FPS inferred from ordinary UI Choreographer callbacks with game FPS.

## v0.4 Pure Cobalt & Solar Glass + standby HUD
- Original static cobalt/orange/yellow light mesh, low-alpha glass surfaces, 1dp specular borders and compact navigation; four real-data circular gauges with unavailable FPS explicitly shown as missing.
- Foreground **standby HUD** is activated by a deliberate user tap, displays a persistent notification, draws one small overlay and runs **no telemetry collector or SQLite recording** until the user taps its REC control. It intentionally displays no fabricated standby measurements.
- REC begins the existing :monitor coroutine and SQLite session; STOP finalizes the session then returns to standby without closing the overlay. Close button or notification exits standby.
- HUD customizer: opacity 30–90%, 3 sizes, compact/Pro layouts, choose CPU/GPU temperatures, CPU/GPU load, RAM, battery net W, optional FPS missing-value indicator, Pro sparkline, red/orange/cyan/violet dot and opt-in blink. Single sample source, no extra polling.
- User must grant Display over other apps permission; ordinary Android background/foreground service restrictions apply, and an Android 14+ `specialUse` foreground-service declaration does not guarantee Play approval.
- Controller-accessible compact 84dp rail retains Live, Sessions, Compare, HUD and Settings pages. O2P Control Center remains unchanged.

## v0.3 stability changes
- Android Quick Settings Record tile remains user-installed; Live and Settings now have an explicit tile-add action (Android 13+).
- Optional small draggable in-game HUD; owned by the active recorder process, no second sampler, detached on Stop. Overlay permission is explicit and OFF by default.
- Fixed navigation clipping and compressed sidebar spacing. New compact status header.
- Known denied SurfaceFlinger FPS probe is **disabled** for this device while an authorized read-only alternative is researched; no child shell subprocesses during recording.
- Recorder uses a safe **2 or 5 second** sampling interval in this build (5 seconds recommended during Eden launch). Previous one-second preference is normalized to 5 seconds.
- No tuning of O2P Control Center, no root privileges. Metroid freeze is not yet attributable to any single component.

## v0.2 testing features
- Battery net watts measured from Android battery current and voltage, with provenance and charging warning. Not CPU/package or wall power.
- Optional SurfaceFlinger per-layer present-rate probe (off by default; often permission denied on Android 13). Does not measure internal emulated-game FPS.
- Durable recorder lifecycle journal and app-specific Android process exit diagnostics, plus non-destructive SQLite v1 to v2 migration preserving saved history.
- Observed CPU policy ceiling readouts are separate from user-entered Stock/ClusterTune labels and never claim the identity of a tuning app.
- Updated Instrument Glass UI: real metric cards and 60-second live chart using existing session readings, not extra sampling.

## Build
JDK 17, Android SDK 36, Gradle 8.11.1, Android Gradle Plugin 8.10.1.
The source does not include a binary Gradle wrapper jar; CI provisions Gradle explicitly.
```bash
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
```
The GitHub Actions **Android debug build** workflow compiles and runs JVM tests, then uploads `app-debug.apk`.

See [development scope](docs/ROADMAP.md), [Play preparation](docs/PLAY_RELEASE_CHECKLIST.md), and [privacy statement](PRIVACY.md). Do not distribute this development build as a finished commercial app.

## Copyright
Copyright (c) 2026 Aleksandar Dukovski. All rights reserved. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
