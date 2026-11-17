# CoreMetrics

> **We analyze how the handheld behaves, not how many frames the game renders.**

**CoreMetrics** is an independent, offline-first Android gameplay telemetry and session-analysis application, complementary to [O2P Control Center](https://github.com/locutus5382/O2P-Control-Center). Its purpose is to measure and compare *what actually happens* with different CPU/GPU profiles — without modifying them.

**Current development status: v0.9.9.8 Lifecycle and Memory alpha** — Five user-selectable styles replace the rejected three: Striker, Ghost, Horizontal Strip, Compact Tactical Card and DUKO Lens. The metric values still come exclusively from existing recorder samples; the recorder, foreground service, no-root permissions, database and user-requested FIFO reports are unchanged. All styles share one overlay owner. DUKO Lens uses translucent frosted fallback instead of risky global game-background blur. Full Android and Odin validation is required before release.

## One-app Quick Start (v0.9.9.8)

1. For fault diagnosis install `CoreMetrics-v0.9.9.8-lifecycle-memory-debug` (`app-debug.apk`). For realistic optimized size/startup testing, install **instead** `CoreMetrics-v0.9.9.8-lifecycle-memory-optimized-test` (`app-optimizedTest.apk`, R8-minified, signed with CI's temporary Android debug key). Both use package `com.locutus5382.coremetrics`; they cannot coexist. The initial in-app guide explains notification, overlay and Usage Access permissions. Back up recordings before any signing-key-related uninstall.
2. Launch Eden/Hades, open Android Quick Settings and tap **CM HUD**. The thin outlined pulse badge becomes a bold illuminated badge in the system accent; the status reads **Ready · Tap REC**.
3. Tap the floating HUD's **REC** button, or the separate **CM REC** tile, to start sampling. The HUD tile only shows/hides the overlay; the REC tile explicitly starts/stops the recording. Both tiles describe active recording even if the HUD is hidden.
4. Long-press the HUD tile to open CoreMetrics' HUD Studio. After recording, open **Sessions / Compare** for local offline analysis.

**Appearance:** Frosted Glass is the default. True Glass, Modern Colorful and Technical Cockpit are available in Settings or through **STYLE ↻**. Themes do not alter telemetry, the floating HUD or sample cadence. The system, not CoreMetrics, controls the active Quick Settings tile color; there is no perpetual glow animation or live tile-temperature polling. Buttons and selection chips now use one-shot 140 ms press feedback throughout all existing app themes; HUD expand/dock uses a single 150 ms fade/scale transition without continuously animating during gameplay.

**RAM and stability changes:** QS tiles read their own `:monitor` process state directly while checking persisted leases for recovery, without unnecessary self-ContentProvider IPC. The UI polls less often outside Live/HUD screens and caches thermal-sensor classification during recorded sampling. Closing the Activity after arming standby remains optional; **no fixed memory saving or LMK immunity is claimed**.

**Migration warning:** Older `.v092themes`, `.v093base`, `.v093themes`, `.preview`, and other side-by-side test variants remain distinct Android packages with **separate SQLite session databases**. Installing the unified APK will NOT import their private sessions. Export any valuable CSVs and diagnostic reports from old builds first, then import CSVs into the unified application. Remove old Quick Settings tiles, revoke older variants' overlay grants and uninstall variants only AFTER preserving data. The canonical package `com.locutus5382.coremetrics` retains its own app-private data when updated with a compatible signing key; a different signing key may require uninstall/reinstall and backup first.

**Validation:** See `docs/PORTAL_V098_ANALYSIS_ACCEPTANCE.md`, `docs/PORTAL_V0975_HUD_ACCEPTANCE.md`, `docs/PORTAL_V0974_STABILITY_ACCEPTANCE.md`, `docs/PORTAL_V0973_INTERACTION_ACCEPTANCE.md`, `docs/PORTAL_V0972_HUD_ACCEPTANCE.md`, `docs/PORTAL_V0971_ANALYTICS_ACCEPTANCE.md` and `docs/PORTAL_V097_TELEMETRY_ACCEPTANCE.md` (and the earlier v0.9.6.1 checklist for onboarding and migration). Source review and successful GitHub unit builds do not substitute for 8 GB Odin 2 Portal real-game testing.

---


## v0.9.9.8 — Foreground lifecycle and background memory (draft alpha)

- **Start foreground first:** START, ARM and lease recovery present a truthful preparing notification before potentially blocking SQLite/lease/journal I/O; HUD options are no longer queried in service onCreate.
- **Release data when hidden:** UI releases 12k-row Sessions/Compare caches onStop/trim, preserving session selections and reloading the original saved points on return; pending DUKO/SAF tasks prevent eviction.
- **Database reads fail visibly:** guarded IO reads for session list, links and sample lists log errors and preserve existing data, while coroutine cancellation is never swallowed.
- **One SQLite helper per process:** Quick Settings no longer opens temporary SQLite helpers on tap; the service reads live defaults after its foreground notification. The UI/:monitor processes retain distinct process-local helpers and SQLite remains authoritative.
- **No speculative gains:** real PSS, jank and startup deadline results require Odin hardware checks. Report FIFO, sensor capture, five HUD themes, schema and report format unchanged.
- Device and CI acceptance: docs/PORTAL_V0998_LIFECYCLE_MEMORY_ACCEPTANCE.md.

## v0.9.9.7 — Reliability and memory hardening (draft alpha)

Follow-up to the independent Claude static review of v0.9.9.4, applied only after comparing its claims with the current branch. Recorder tick errors now support bounded recovery and honest dropped-sample journaling; permanent DB failures still terminate. User-requested DUKO report FIFO exports perform a low-memory preflight and best-effort OutOfMemoryError cleanup, without changing queue semantics. Compare benchmark analysis moves to Dispatchers.Default. Session diagnostics' rolling 60-second median and distribution quantiles use reusable primitive arrays to reduce garbage, while GPU parsing reuses its regex. The five HUD themes, recording database schema v9 and DUKO report format v0.9.9 remain unchanged. Hardware PSS and performance gains are unverified until Odin tests.

Acceptance protocol: docs/PORTAL_V0997_CLAUDE_REVIEW_ACCEPTANCE.md. This is a draft until CI and device validation pass.

## v0.9.9.6 — Five-theme HUD overhaul (draft alpha)

- **Five fully selectable visual modes** in HUD Studio: Striker (new default), Ghost, Horizontal Strip, Compact Card and DUKO Lens. Old pill/card/ribbon choices automatically normalize to their nearest supported replacement. Each style groups the same real telemetry differently and uses existing REC/STOP, dock, lock/unlock and service callbacks.
- **True swipe-to-edge auto-collapse** from metric zones; no accidental collapse on a short or vertical movement. The collapsed overlay window is 44dp square; Lens paints only a thin 2dp bezel line and status dot inside the accessible target. Tap to expand.
- **Distinctive DUKO Lens** with monospaced key/value entries, sharp bracket framing, explicit red elapsed-time STOP beacon, text [ LOCK ] and white-only readings except display-only orange/red thermal advisories. No real-time expensive screenshot capture or global backdrop blur.
- **No guessing** of game title or FPS; system RAM remains labelled as system memory. Never imply sensor reading is certified thermal throttling or per-game performance.
- **Safeguards:** One WindowManager HUD owner, one independent unlock handle when locked, unchanged 2s/5s REC sampling, no new permissions, SQLite schema or automatic reports. Builds are drafts until tests and Odin gameplay acceptance pass.
- Acceptance criteria: docs/PORTAL_V0996_FIVE_HUD_ACCEPTANCE.md.

## v0.9.9.5 — HUD Layout Studio, with parallel stability work

- HUD Studio now offers **Classic pill (default)**, **Compact Glass Card** and **Horizontal Telemetry Ribbon**, with CoreMetrics' familiar navy/cyan styling and illustrative previews. Historical rounded-card option ("pro") maps to Glass Card.
- Switching layouts reconfigures the **same** WindowManager root and existing REC/STOP/dock/lock controls in place. No new overlay, permissions, recorder services, background sampling or SQLite migration.
- Glass Card: slim header, two-column sensor presentation, controls beneath telemetry. Ribbon: compact horizontal sensor presentation, one-line when space permits and wrapping on narrow screens rather than silently omitting metrics.
- Existing size, transparency, manual metric selection, conditional OS thermal warning, optional BAT T outlines, REC indicator and Quick Settings behavior continue for each layout. No invented game FPS and no real-time GPU-heavy blur.
- Unit tests cover defaults, legacy settings, metric order, narrow ribbon behavior and sizing. Android instrumentation checks persistence and intent handoff.
- **Parallel priorities:** stabilize GitHub API 33 emulator infrastructure, then verify process/overlay ownership, recording recovery, DUKO queue cancellation, peak memory and report presentation on Odin. See `docs/PORTAL_V0995_HUD_LAYOUTS_ACCEPTANCE.md`.
- VersionCode **48**; SQLite **v9** unchanged. Keep this feature as draft until debug/R8 builds and device acceptance pass.

## v0.9.9.4 — Pre-Odin hardening and test diagnostics

- **On-demand diagnostics:** Settings → View process-exit & lifecycle log now includes a read-only check of the latest five saved sessions (counts, source timestamp bounds, basic consistency flags), plus point-in-time PSS/Java heap of the UI process. No game labels or raw telemetry are included in this new section.
- **No automatic work:** Diagnostics are collected only when a user presses the button. All DUKO report preparation remains explicit and FIFO, one request at a time. No added background services or sampling loops.
- **Bounded Android CI:** API 33 emulator instrumentation has a 20-minute command timeout inside the existing job timeout, and stores XML/HTML results when available.
- **Compatibility:** VersionCode **47**, SQLite still **v9**, no new permission, recorder, overlay, Quick Settings or hardware-control changes. Use the v0.9.9.4 debug artifact for the first combined Odin validation.
- **Acceptance:** `docs/PORTAL_V0994_PRE_ODIN_ACCEPTANCE.md`. Draft-only until full CI and real Odin gameplay testing.

## v0.9.9.3 — Reliability & Reporting hardening

- Restored realistic Android v7/v8 migration test fixtures, corrected archive-restoration and SAF assertions, and retained production transaction and deletion safeguards.
- In-place primitive quantile sorting avoids up to ~18 MB of temporary source-array copies in a maximally populated 250k-sample, nine-metric report (engineering estimate, not measured Odin PSS savings).
- Offline DUKO single- and multi-session exports check Android coroutine cancellation during long sample scans, avoiding continued analysis of abandoned export jobs. Only one DUKO export can run at a time; blocked SAF requests clean up their newly-created documents.
- CI runs API 33 instrumentation and retains emulator test reports even on failure. Both debug and R8 optimized APK builds remain independent gates.
- No new Android permissions, recorder services, HUD redesign, performance tuning controls or database migration.
- See `docs/PORTAL_V0993_RELIABILITY_ACCEPTANCE.md`. This version remains a draft alpha until emulator and Odin acceptance.

**On-demand FIFO reporting (v0.9.9.3):** Every DUKO PNG/PDF is created only after an explicit button press and user-selected document destination. Multiple requests are queued and prepared in click order; **only one report runs at a time**, including its file picker. Sessions and Compare show a queue with progress, Cancel, and Remove controls. The queue survives screen rotation but is not a persistent background worker. During requested exports, the app shows an elapsed-time and sampled PSS/Java-heap estimate, not a measured true memory peak.

**Large-recording optimization:** DUKO now uses one reusable 15-field cursor scratch row instead of one 25-field Point per sample, keeps sliding-window utilization counters without repeated list allocations, and reuses bounds during source scans. The detailed five-session PDF splits matrices for legibility and explains ineligible sensor comparisons; the PNG remains a compact overview. See `docs/PORTAL_V0993_RELIABILITY_ACCEPTANCE.md`.

## v0.9.9.2 — DUKO Comparison Report (2–5 recordings)

- **How to use:** Compare → **DUKO COMPARISON REPORT** → select 2–5 recordings → pick the baseline and any declared test variable → **DUKO Comparison PNG / PDF**. The Android document picker controls where the offline output is saved.
- **Complete source recordings:** Each comparison uses v0.9.9.1's full persisted chronological session data (maximum 250,000 source samples per recording), never the 12k-preview or a partial selection. All processing happens on demand off the main UI thread.
- **Shared evidence matrix:** Nine source sensor metrics, P50/P95 sample percentiles, coverage and signed P50 difference from the chosen baseline. Ineligible numeric deltas are shown as unavailable, not 0 or a fake performance score.
- **Provenance/confidence:** Unknown or different original hardware, unsupported/unvalidated GPU source, invalid discharge coverage or mismatched battery source prevent corresponding numeric comparisons. User-tagged driver, fan, ClusterTune, rendering scale and performance mode are treated as declarations, not hardware verification.
- **Reporting:** One-page 1600×2200 PNG plus multipage native vector A4 PDF containing source metadata, complete methods and paginated warnings. These reports cannot rank gaming FPS, compare chip power, prove game-scene equivalence, or claim one driver wins.
- **Preserved:** v0.9.9.1 single-session DUKO, legacy A/B and A/A, original scorecards, Quick Test Profiles, CSV/TXT, HUD, QS, recorder, offline/no-root design, four themes. VersionCode **45**, SQLite stays **v9**.
- **Validation:** `docs/PORTAL_V0992_DUKO_COMPARISON_ACCEPTANCE.md`. Keep both PR #39 and the comparison PR in draft until real-device tests are accepted.

## v0.9.9.1 — DUKO Session Insight (single recording)

- **How to use:** Sessions → Analyze a completed recording → **DUKO Session Insight** → **DUKO PNG** or **DUKO PDF**. Choose a destination via Android Storage Access Framework. Report computation and drawing run offline on IO, not on the REC/HUD thread.
- **One engine, two formats:** an independently testable `DukoSessionInsight` analysis model powers both the 1600 × 2200 light PNG executive summary and native Android vector PDF (summary, evidence/glossary, chronological event pages). PDF is preferable for detailed review; PNG for small shareable overviews, not giant scroll images.
- **All source samples:** full chronological SQLite cursor up to **250,000** samples. A separate bounded 160-interval cadence query and exact primitive sample quantiles avoid materializing whole Point objects. Chart traces preserve bounded min/max extrema and never intentionally join missing sensor readings or recording gaps. Ordinary UI analysis retains its 12k preview limit and warns as before.
- **What it explains:** observed CPU/GPU/battery temperatures, system CPU/GPU load, valid whole-device discharge W and Wh, measured power coverage, BAT T warm/hot time, changed CPU ceilings, Android thermal policy, memory pressure, sustained high utilization and quality limitations. Each note is **observed or pattern evidence**, with no invented game FPS or causal throttling conclusion.
- **No fabricated data:** unmeasured values remain unavailable; unknown GPU paths or original-device identities are disclosed. Battery capacity/runtime estimates remain disabled unless validated in a future build.
- **Preserved:** existing TXT/CSV/PNG scorecards, A/B, A/A, profile tagging, HUD and firmware capability Diagnostics. No extra background sampling, permission, network requests or database migration. SQLite stays v9.
- **Validation:** `docs/PORTAL_V0991_DUKO_SESSION_ACCEPTANCE.md`. CI tests cannot substitute for executing on-device PDF/PNG instrumented tests and inspecting printed A4 pages.

## v0.9.9.0 — Device & Measurement Foundation (portability)

- **No fixed battery capacity.** The previously hardcoded 30.8 Wh Odin runtime estimate was removed from session diagnostics and CPU-cap phases. Current actual battery-terminal power and time-integrated Wh remain reported when valid. Read-only `energy_full_design` and optional `charge_full_design` plus a separate nominal voltage provide one-shot, explicitly *unvalidated* design-capacity candidates. No hidden `PowerProfile` reflection.
- **Discovered CPU policies:** active recorder reads available `policyN` directories, rather than assuming 0/3/7, and emits `CPU_POLICIES:` cap readings. The parser understands their actual numeric IDs, but does not infer an architecture or identify who applied a cap. Historical P3/P7 sessions remain legible. Missing and incomplete readings remain unknown.
- **KGSL/Mali source adapters:** strict parsers for supported read-only Qualcomm KGSL GPU percent/ratio and Arm Mali percent nodes, plus frequency-only GPU devfreq candidates. No fabricated utilization from frequency, no root, and no unconditional claim that readable sysfs paths are calibrated. A bounded recheck handles transiently unavailable nodes.
- **Recording origin & measurement sources:** SQLite **v9** stores Android manufacturer, model, device code and API with each new recording, plus per-sample `gpu_source`. Header-mapped CSV adds nullable provenance fields at the end. The portable archive retains the original recording-device identity after restore; opening on a second phone never rewrites its provenance.
- **A/B and A/A cautions:** device identity missing/different and GPU sources missing/different/unvalidated are explicitly disclosed in observational analysis. Existing driver comparisons remain noncausal and do not claim frame performance.
- **Settings → Device telemetry compatibility:** an on-demand, offline read-only diagnostic listing hardware policies, related core IDs, battery counter/design Wh candidates and GPU-source status. It does not poll during standby or add any new background services.
- **Validation:** `docs/PORTAL_V0990_DEVICE_COMPAT_ACCEPTANCE.md`. Device/ROM behavior must be tested on real hardware; CI assembles instrumentation but does not execute it.

## v0.9.8.3 — Measurement Integrity & Reproducibility

- **Battery power measurement evidence:** Stored `battery_power_source` now distinguishes the actual current and voltage origins and explicitly marks their update-age synchronization as unverified. Large current polarity / charging-status contradictions produce missing wattage rather than false battery discharge. Source changes generate comparison cautions; CPU/GPU power and game FPS remain unmeasured.
- **On-demand capability probe:** one-shot battery current, charge counter and energy counter availability; dynamic, bounded CPU frequency-policy discovery and cumulative `time_in_state` counters. No extra per-tick sensor queries, unsupported counter assumptions or universal CPU/GPU critical limits.
- **A/A repeatability:** Compare tab offers a collapsed-by-default 2–5-run panel for sessions with the same Quick Test Profile. Reports medians of **run-level** measurements and observed ranges, rejects truncated runs, warns on mismatched tags and battery provenance. No FPS, hypothesis test, confidence interval, workload-equivalence claim or automatic driver ranking.
- **Longer CSV import:** streaming atomic import now supports up to 100,000 samples/session with a clear safety limit. The 12,000-sample **on-screen preview limit remains** and is prominently disclosed; full-session CSV export remains streaming.
- **Portable data archive (alpha):** Settings → Export archive ZIP / Append from archive ZIP. Versioned manifest plus raw session CSVs, profiles, manual tags and A/B links. Restore uses new session IDs and remaps links in an outer rollback transaction; no overwrites or delete operations. Device/HUD defaults included only for reference, NOT applied on a new Android device. Explicit size limits apply; **do not rely on this until actual Android instrumentation restore/corruption tests have run**.
- SQLite remains schema **v8** and telemetry CSV header remains compatible. Existing recorder, QS, BAT T and HUD operations were not redesigned.
- **Validation:** `docs/PORTAL_V0983_MEASUREMENT_ACCEPTANCE.md`. v0.9.8.3 is a development alpha; a successful CI unit test/build does NOT mean on-device restore, sensor provenance or lifecycle behavior has been verified.

## v0.9.8.2 — Stability & Analysis Validation

- SQLite **v8** adds one authoritative, cross-process recording-settings row (label, configuration, cadence, auto-HUD, next test-profile ID and HUD option JSON). The old per-process preferences are a **one-time migration input** only. Both Quick Settings tiles and the monitor service read this same database; explicit Intent extras still take precedence.
- The selected Quick Test Profile is saved in a real SQL transaction and snapshotted during the existing session-start transaction. Deleting a selected preset resets only the future selection. Earlier sessions retain their strings, even when a shortcut is renamed.
- Compose Save captures immutable values before switching to Dispatchers.IO; UI deletion clears editor fields only after confirmed success. UI defaults go through a single ordered SQL writer, and ARM awaits durability before optionally closing the Activity.
- A/B analysis no longer compares percentages of *relative* high GPU activity computed against incompatible within-run quartiles. It now reports a caution for non-overlapping absolute GPU-load interquartile ranges. No performance-equivalence claim is made from load alone.
- Busiest system CPU core shifts require nontrivial observed load. Incomplete 12,000-row analysis now shows an obvious top-level warning; long-session segment exports remain disabled, and full-session CSV export is unchanged.
- v0.9.8 analysis, no-root telemetry, v0.9.8.1 BAT T, current compact HUD, REC/STOP/LMK safeguards and report-format v0.9.8 are retained. **Validation:** `docs/PORTAL_V0982_STABILITY_ACCEPTANCE.md`. Android instrumentation is assembled in CI but must still run on a device.

## v0.9.8.1 — Optional BAT T warmth advisories and battery-health alert

- **HUD Studio → Battery °C** is optional and OFF by default. The label `BAT T 35.2°C` uses the already sampled battery temperature from the existing recording snapshot; battery-terminal `BAT 4.2 W` stays independently selectable.
- **NORMAL (<39°C):** plain text, NO outline. **WARM (>=39°C):** thin yellow rounded outline around only BAT T. **HOT (>42°C for >=15 seconds):** thin orange outline. HOT releases to WARM only after <41°C for >=10 seconds; WARM releases to NORMAL only after <38°C for >=10 seconds.
- Time-based hysteresis works at both 2s and 5s cadence. Missing/invalid temperature prints em dash and clears pending confirmation; it never becomes an invented safe value. The inline span reserves constant width to avoid warning-on HUD layout jumps.
- The *separate* Android `BATTERY_HEALTH_OVERHEAT` status, when available in the already-read sticky battery broadcast, displays a static red OS/device alert accent even if BAT T is hidden. It is HUD-only in this version; the existing SQLite schema/CSV contains battery temperature but does not yet store this new health status.
- CPU/GPU temperatures and existing OS thermal warnings remain unchanged. No device-specific CPU/GPU critical limits, no root, no extra battery polling, no alarm loop. Thresholds are **application-defined warmth advisories**, NOT manufacturer safety or throttle limits.
- Verify the new on-device UI and permission persistence with `docs/PORTAL_V0981_BATTERY_ACCEPTANCE.md`. Android instrumented tests are assembled in CI but still require on-device execution.

## v0.9.8 — Analysis Intelligence and reusable test conditions

- **Quick Test Profiles:** user-created/editable/deletable named shortcuts for game/track, graphics driver/version, render scale, Odin performance mode, fan profile and ClusterTune label. Live offers a next-REC selector; Sessions → Edit can apply a profile to a completed session. Preset changes/deletions do **not** rewrite historical data. Manual metadata is not proof that the hardware setting was active.
- SQLite schema **v7** introduces a reusable `test_profiles` table and four nullable per-session snapshot fields. Additive migration from v1–v6 preserves existing data. CSV exports gain `performance_mode`, `fan_mode`, `cluster_tune_mode`, `profile_name` columns at the END, preserving backwards-compatible header-mapped imports.
- **Significant events:** offline CPU P3/P7 cap transitions, system busiest-core distribution shifts, relative higher/lower GPU activity periods, continuity gaps, OS thermal pressure and consecutive high system memory PSI readings. A 120-second sustained utilization event requires >=95% load in >=95% valid samples with >=90% expected coverage and no large gap; these are NOT FPS, game-thread migrations or causality.
- Single-session analysis keeps its existing conclusion-first view and adds a compact event timeline. A/B comparison now asks which variable is intentionally changed (driver, Odin power, fan, ClusterTune) and warns about other known or missing test conditions without pretending game scenes are synchronized.
- Report-format version **v0.9.8**; session CSV metadata extended, TXT analysis stays offline, current PNG scorecards and A/B timeline retained. No new sensor reads, root requirement, HUD redesign, or recorder timing/persistence changes.
- Device-specific thermal warnings and optional battery-temperature HUD display remain a separate v0.9.8.x decision, dependent on ROM validation. DUKO PNG/PDF reports are deferred to v0.9.9.

## v0.9.7.5 — Compact HUD, explicit corner parking, clear REC state

- Uses one side-by-side pill: 3-line metrics on the left and a small two-level STOP/REC, collapse and lock control cluster on the right, without the previous dead bottom-left region. In-game metric/opacity editing is moved to the existing HUD Studio; there is no permanently visible settings gear.
- Locking removes the action cluster and reserves only the independently touchable 48dp unlock target over the now-compact, otherwise touch-through telemetry card. Retains Android 12+ touch safety and tracked WindowManager ownership.
- Dragging into a corner **parks** the expanded HUD about 4dp from the screen edge. It never collapses merely because a border was touched. `⇥` is the intentional collapse control; tapping the edge tab expands. Pure parking geometry is covered by JVM tests.
- Collapsed REC has a red outline, a visible `● R` and elapsed time updated only when an existing REC sample arrives. READY has a cyan `CM / READY` tab. No perpetual animation or extra polling loop.
- Drops the permanently displayed `OS NONE` badge. Normal/unavailable Android thermal status gets no separate HUD indicator; only actual Android OS warnings show a thin amber/red left accent. Absolute CPU temperature does **not** define a throttling threshold.
- HUD Studio preview/copy matches the revised controls and no longer implies OS status NONE guarantees thermal safety. CSV, analysis engine, lifecycle, permissions and sampling cadence are not changed.

## v0.9.7.4 — Bounded standby recovery and observable recorder overhead

- After an Android-killed user-armed standby is restored, the new standby is **not automatically restartable again**; a fresh explicit ARM enables another one-shot recovery. This prevents a chain of standby LMK restart requests while preserving existing active REC and terminal STOP reconciliation. Android OEMs may still decline the first restart.
- Recording samples are still inserted immediately in SQLite. At most 256 recent ticks are summarized in a **single** `RECORDER_TIMING` lifecycle event when recording finishes: collector, SQLite, HUD dispatch, full tick and scheduling lateness P95. These are elapsed durations, not CPU usage or extra CSV fields.
- Optional Android headroom and PSI reads retry with backoff after repeated unsupported/failed responses, and resume normal sampling if valid data returns. PSI 0% remains a valid reading; skipped probes stay null. The existing 10-second headroom minimum interval is preserved.
- One-shot capability diagnostics now inspect multiple raw GPU-frequency sysfs sources, not a privileged probe or proof of frequency scaling.
- HUD geometry cache uses primitive fields; drag updates are coalesced to one WindowManager layout request per display frame and flushed on gesture end. Cosmetic HUD preferences use `apply()`, but recorder lease and STOP intent retain synchronous `commit()`.
- No UI theme/layout additions, FPS claims, new storage schema or unverified savings claims. See `docs/PORTAL_V0974_STABILITY_ACCEPTANCE.md`.

## v0.9.7.2 — Readable in-game HUD and simple-first analytics

- Expanded HUD shows telemetry above a short, right-aligned action row. Moving the HUD still works; its initial position stays near the right edge so touch controls do not sit across the game center.
- Collapsed HUD uses a larger high-contrast `CM / ● REC` edge tab; OS-wide thermal policy is a labeled `▌ OS` status badge rather than a tiny dot. Neither is a CPU/GPU throttling diagnosis.
- HUD Studio has an illustrative, size/layout/opacity/metric-responsive preview, explicitly marked **NOT LIVE**. The global `STYLE ↻` shortcut was removed; appearance stays under Settings.
- Session analysis leads with the basic reading summary and two optional sections: **Adjust interval / export** and **Advanced diagnostics**. When a recording contains multiple observed CPU frequency ceilings, the summary warns that its full-session averages mix configurations.
- Single-session PNGs display actual **Recording continuity** and gap counts rather than mislabeling the legacy composite comparison-input score as "Telemetry integrity". A partial recording receives a caution badge even when it has final status COMPLETED.
- Does not alter recording recovery, existing Quick Settings tiles, firmware access, sampling cadence, database schema v6, CSV columns, network/root permissions or report-format v0.9.7. No claim of LMK immunity; see `docs/PORTAL_V0972_HUD_ACCEPTANCE.md`.

## v0.9.7.1 — Selected-window analytics and evidence guardrails

- Evidence-based A/B observations summarize qualified battery-terminal discharge, median GPU temperature and median GPU utilization differences. No faster-gameplay, efficiency winner, bottleneck or throttling verdict is inferred.
- A/B warnings now include incomplete selected-window sampling, early GPU-temperature mismatch and disparate measured final-minute CPU slopes; warnings describe comparability limits, not physical thermal equilibrium.
- The A/B interface distinguishes **recording integrity** from the legacy composite comparison-input score. Exports include both integrity verdicts, continuity and factual observations for the selected window.
- Offline timing analysis derives mean interval, P99 non-gap timing jitter (only with 20+ valid inlier steps), and count of intervals exceeding twice the inferred target cadence. These are not independently proven lost samples.
- Same Android package; unchanged REC/HUD life cycle, 2s/5s sampling, SQL schema v6, CSV sample fields, report-format v0.9.7, permissions, privacy and offline-only operation. Independently shifted windows and extra per-tick storage-overhead fields are deferred.
- **Test candidate:** See `docs/PORTAL_V0971_ANALYTICS_ACCEPTANCE.md`. No merge to main without Odin validation.

## v0.9.7 — Thermal HUD and trustworthy recording integrity

- A tiny independent device-wide thermal dot appears only during REC: white = no Android OS thermal alert, amber = Android mild/moderate alert or forecast headroom >=0.85, red = Android severe-or-higher alert or headroom >=1.0, grey = unavailable. It is advisory and never identifies a specific CPU/GPU throttle. There is no standalone HUD sensor polling loop and standby remains idle.
- Rate-limited Android thermal headroom (10s between calls), thermal status, optional swap used, optional memory PSI some avg10 and per-sample collection duration are saved as nullable readings. Headroom is recorded only when freshly measured.
- SQLite v6 adds six nullable fields with migration from v1-v5 and name-based CSV import compatibility. Historical FPS fields stay readable for old CSVs but are no longer shown as a measurement in the UI. CoreMetrics does not measure game smoothness.
- The session summary separates recorded-sample continuity from the older composite comparison-quality score and presents at most three bounded, observational findings; avoids automatic causal throttling claims.
- First validate source/JVM build and SQLite migration via CI. **Do not merge into main or trust device-specific values before Odin 2 Portal verification**.

## v0.9.6.6 — Audit, session-analysis correctness and diagnostic UX

- Analysis reuses SQLite sample rows for unchanged session IDs and sample counts. Changing driver, scale, profile, game note or warm-up selection **recomputes derived benchmark quality from cached samples** rather than copying an out-of-date score or rereading 12,000 points. If the original recording gains samples, the changed persisted row count invalidates the cache and triggers a new read.
- Settings diagnostic snapshots (thermal headroom, swap, PSI, policy7 time_in_state, process exits and journal) execute on a background IO dispatcher, not the Compose UI thread; a loading label blocks accidental duplicate requests and disables Copy while the new snapshot is still being collected.
- Manually edited game notes now display **Saving…**, success or error next to Save; the editor prevents double-tap writes while a save is outstanding.
- OS thermal-status probe renders a named state beside its code and a readable local timestamp; remains a **one-time report**, not a recorded telemetry metric or FPS/throttling inference.
- Exports now correctly identify app version **v0.9.6.6** and keep the independent report **format v0.9.6**. A JVM regression test checks that reported app version matches the APK's generated `BuildConfig.VERSION_NAME` during build.
- No changes to Android service/lease lifecycle from v0.9.6.5, Quick Settings metadata, HUD touches/visual themes, root/privacy policy, sampling interval, database schema or immediate SQLite insertion. Safe hardware acceptance is still needed before any merge or public release.

## v0.9.6.5 — On-demand Odin capability validation (no sampling changes)

- Open **Settings → View process-exit & lifecycle log** and copy or export the report to inspect thermal status, 10-second thermal headroom (may return unavailable/NaN), Linux SwapTotal/SwapFree/SwapUsed, memory PSI some/full lines, and CPU policy7 `stats/time_in_state` readability.
- All file reads are bounded; inaccessible/empty/unsupported sources report their status without terminating the diagnostics export. Frequency residency counters are **cumulative kernel ticks**, not instantaneous clocks, utilization, power or observed throttling.
- Results represent the moment of report export, **not past process-exit conditions**. No new root/Internet permissions, no data collection during standby, no additional sensor polling, no database/CSV migrations, and no new gameplay HUD controls.
- This is an evidence-gathering bridge to decide what is genuinely supported before planning v0.9.7. Still a test release: keep draft PR #27; don't merge to main before testing on the Odin.

## v0.9.6.4 — STOP-state policy, metadata/analysis and diagnostic preparation

- **REC vs READY:** STOP finalizes the same SQLite session and normally keeps the HUD READY; the READY lease following STOP is intentionally *not* eligible for automatic OS resurrection. An explicitly armed standby may recover after LMK, and recording itself may recover the original ID.
- **Durable STOP intent:** a failed terminal lease write is recorded. The service attempts to clear a stale recovery lease rather than silently allowing an unintended recorder restart, but cannot guarantee recovery when persistent storage itself is failing.
- **Fast metadata editing:** changing a driver, resolution, game note or configuration refreshes session metadata in the existing analysis report without rereading the original 12,000-point sample dataset.
- **A/B clarity:** all eight PNG measurement rows are represented in the in-app A/B table, selectors include shortened game notes, PNG comparison headers show independent A/B sessions, and exports distinguish app build version from stable report format version.
- **Data and labels:** manually edited notes strip control characters; the legacy all-at-once metadata method now validates both tags and refuses editing active RECORDING rows. HUD Studio text describes the new frosted edge docks.
- **Read-only optional capability check:** Diagnostics exports can report Android thermal status, available 10-second thermal headroom, swap *allocation*, and memory PSI if permitted on the device. These are one-shot probes at export time, NOT readings at past LMK events, not per-session telemetry, and do not alter sampler cadence or DB schema. Send the diagnostic export after Odin testing to decide whether to record these metrics in v0.9.7.
- **Release policy:** test on actual Odin 2 Portal; CI builds the Android instrumentation APK but does not run it. Never merge to main or claim LMK immunity based on CI alone.

## v0.9.6.3 — HUD and lifecycle stabilization

- **Quiet Frosted Glass drawer:** neutral translucent white-on-slate highlights for enabled measurements, no saturated orange/blue fills, and simple muted text with no tile background when disabled. The REC action remains identifiable by its small status dot.
- **Adaptable HUD:** telemetry naturally sized instead of a weighted 475dp text slot. Actual overlay width varies with enabled metrics and temporarily expands for drawer controls. Three edge-dock positions (left, right, top) resize the WindowManager window itself; a labeled frosted CM/REC tab replaces the unexplained colored line.
- **Quick Settings truthfulness:** distinguish user's HUD request, actual overlay attachment, temporary pause while CoreMetrics is on-screen, recording, saving and pending terminal STOP. Click-state refresh follows service command processing. HUD tile never starts a second recording.
- **Durable explicit STOP:** persist stop_requested before recorder coroutine cancellation; only clear the lease after SQLite terminal status is saved. If Android kills :monitor while finalizing, the original session can be finalized WITHOUT resuming sampling; no new ID allocated.
- **Session metadata:** inline saving/success/failure feedback and editable, persisted display of Custom graphics driver and resolution tags; keyboard Done triggers save. Invalid values are rejected, not silently converted to NULL with a success message.
- **Release decision:** the user's Portal v0.9.6.2 data showed multiple Android LOW_MEMORY kills of the independent monitor process but successful same-session REC recovery. v0.9.6.3 improves STOP safety and UI polish; actual LMK behavior, overlay geometry and keyboard focus still require device acceptance.

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
