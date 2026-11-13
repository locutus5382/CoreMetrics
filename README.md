# CoreMetrics

**CoreMetrics** is an independent, offline-first Android gameplay telemetry and session-analysis application, complementary to [O2P Control Center](https://github.com/locutus5382/O2P-Control-Center). Its purpose is to measure and compare *what actually happens* with different CPU/GPU profiles — without modifying them.

**Status:** v0.8.3 Portal UI/controller test branch, with separately installable Frosted Analytics and optional Themes test APKs. Prior v0.8.2 was tested on the Odin 2 Portal; v0.8.3 fixes still require physical verification. Not merged into main or approved for Google Play. Source visible for development review only. Copyright (c) 2026 Aleksandar Dukovski. All rights reserved.

## Planned product
- **Live:** CPU/SoC and GPU temperature, CPU/GPU load and clocks, RAM, battery. All readings identified by source, with explicit Unavailable fallback.
- **Sessions:** User-started foreground recording with a persistent Stop notification, local SQLite storage and CSV exports.
- **Compare:** Stock / ClusterTune / Custom run labels, elapsed-time-aligned thermal and utilization charts and differences. A label is user-entered, never proof that a tuner is active.
- **HUD Studio:** Opt-in no-sampling standby capsule, tap REC to start the existing collector, STOP to save and return to standby, plus opacity, size, compact/Pro layouts, metric visibility, mini sparkline, blinking dot and color customization.
- **FPS:** Opt-in experimental SurfaceFlinger per-layer frame-present probe. Standard app UID may lack DUMP permission; a clear error status is shown, never invented FPS. Do not present display refresh or emulator-internal timing as actual delivered game FPS.

## Security, privacy and platform
- Separate Android application ID: `com.locutus5382.coremetrics`. Separate database and notifications; no dependency on O2P.
- No `INTERNET` permission, analytics SDK, account, boot receiver, automatic background recorder, Accessibility service, kernel writes, frequency modifications or `oom_score_adj` changes. Optional `SYSTEM_ALERT_WINDOW` permission is needed only for the user-enabled, single-window HUD.
- Optional Usage Access for identifying app packages. It cannot identify a game running inside an emulator.
- Google Play build baseline uses **compileSdk/targetSdk 36**, minSdk 31. User-initiated `specialUse` foreground recording must be approved by Play Console. Neither target SDK nor a manifest declaration guarantees approval.
- No PServer in initial Play-compatible version. A separate strictly read-only Odin variant may be designed later only if it provides validated value and clear permissions; a read-only observer cannot claim LMK immunity.
- On-device collected readings and session history stay in app-private storage unless the user chooses to export them.

**Parallel test build (recommended):** GitHub Actions now also generates **CoreMetrics Preview**, package `com.locutus5382.coremetrics.preview`. It has a separate provider authority, tile, preferences and SQLite database, so it can coexist with the v0.5 installation even if CI debug signing keys differ. Export v0.5 CSVs and import them inside Preview to compare the same recorded runs. Do not use the regular v0.6 debug APK if you want to preserve v0.5 installed simultaneously. Preview is not an upgrade/migration and will not automatically see v0.5 private sessions.

## Optional v0.8 Themes build (separate side-by-side APK)

Built on the **same v0.8 analytics implementation**, adding the three UI finishes from Gemini's visual design reference:
- **Frosted Glass (default):** original CoreMetrics cobalt/navy/orange glass gradients and cards **unchanged**.
- **Liquid Glass:** static high-contrast navy panels with brighter cobalt/specular hairline borders; no blur applied to the card's content, no per-frame animated sheen or forced compositor layer.
- **Tactical Flat:** OLED black background, opaque slate cards, 1dp borders and mint/cyan/orange gauge arcs.

The selector is in the **Settings** screen only of `com.locutus5382.coremetrics.v08themes`. It persists for that separate app; the baseline `v08base` package never displays it, and always uses Frosted Glass. HUD/WindowManager overlays, recorder, SQLite schema, permissions and FPS Lab are identical to the baseline and remain outside theme scope. Screenshots are conceptual references, not pixel-perfect contracts for the Odin's landscape display.

This build deliberately **does not copy** Gemini's `.blur(20.dp)` card modifier (that would blur text and controls), remove Material ripple/accessibility states, or run perpetual reflection animations. The adapted design uses static Compose gradient/border primitives.

Install `CoreMetrics-v0.8-analytics-only` and `CoreMetrics-v0.8-themes` APKs side by side; use only one recording overlay at once and import equivalent CSVs into both databases for comparison.

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
