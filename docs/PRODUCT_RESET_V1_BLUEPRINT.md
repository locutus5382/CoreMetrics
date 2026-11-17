# Product reset v1 — preserve the science, rebuild the experience

**Status:** Design and architecture RFC; no existing runtime code changes on this branch.
**Baseline:** the development source from `feature/coremetrics-v0999-ui-io-hardening`, itself based on v0.9.9.8. **Do not merge or rename the Android applicationId now.**
**Decision:** DUKO is the branded comparison report for 2–5 sessions; individual recordings get in-app analysis, not a primary single-session DUKO PDF.

## Why reset

User screenshots from Odin 2 Portal on 17 Nov 2026 show:
- Oversized, cluttered HUDs with irregular typography, line breaks, labels over game art, colored outlines without hierarchy, separate lock target and control chrome competing with data.
- The ultra-thin in-game ribbon wraps BAT temperature units and collides visually with another emulator's magenta FPS/FT line. **That FPS overlay is not verified CoreMetrics FPS.**
- A collapsed 44dp badge reduces to an unlabeled temperature; a locked card remains large and obstructive.
- Quick Settings reports CM HUD active while its own mode can be paused/hidden: `HudTile.refresh()` marks ACTIVE on requested+attached instead of actual visible state; screenshot shows two active tiles with ambiguous meanings.
- A "Save preset" attempt exposes a raw `SQLite_CONSTRAINT_UNIQUE` error; normal name conflicts must be human-readable with an inline recovery action.
- Session rows expose CSV/Analyze/Edit/Report on every entry; Settings exposes lengthy raw diagnostic text; session metadata expands into long technical forms.
- The telemetry log records a successful recovery of session #8 after monitor restart, with multiple Android LOW_MEMORY exit records in close temporal proximity. This is evidence of recovery but not proof of the exact cause of every process exit.
- Baseline one-shot UI PSS ~101 MiB while report exported, **not** simultaneous monitor PSS or a measured peak.
- Build note: v0.9.9.9 CI initially had 245/246 JVM tests pass; stale app/report provenance version expectation was fixed in the source after the failed run. New CI must pass.

## Product thesis

A **local-first, read-only Android gaming telemetry lab** for handheld players. Two distinct contexts:
1. **Play**: start/stop REC with a tiny, durable, highly legible floating overlay; no configuration clutter over a game.
2. **Understand**: inspect meaningful session findings and compare controlled tests; no false promises of verified FPS, per-game chip power or causal thermal throttling.

A successful user journey: configure test context once -> launch game -> tap REC in game -> stop -> inspect 3 actionable findings -> select matching runs -> DUKO Comparison.

## Capabilities: keep / redesign / defer / remove

| Current capability | Decision | Rationale |
| --- | --- | --- |
| Sampling, non-root sensor abstraction, 2s/5s cadence | KEEP and validate | Core product functionality; no new polling in standby |
| Session persistence, restart lease, resume same ID, lifecycle journal | KEEP with a new state coordinator | Proven recovery path, but complexity and repeated LMKs need device profiling |
| CPU/GPU temperature, system utilization, clocks, Android thermal, power, memory PSI if permitted | KEEP with provenance | Display available values only; never imply game-thread attribution |
| Session quality/gaps, events and sustained-regime analysis | KEEP and elevate | Differentiator when reported with confidence and user decisions |
| Benchmark/diagnostic/segment/session analyzers | KEEP but unify the presentation | Repeated analytics panels and charts confuse users |
| A/B analysis, repeatability, confidence and DUKO comparison | KEEP, flagship | Comparative evidence is the product advantage |
| Quick Test Profiles/manual driver, scale, fan and ClusterTune tags | KEEP but redesign as Test Setup | Metadata improves reproducibility; user entered, not hardware-verified |
| CSV export and portable ZIP archive/restore | KEEP | Data ownership, safety and auditability |
| Single-session DUKO PDF/PNG main entry points | REMOVE FROM PRIMARY UX | Option B decision: single-session insights live in app; archive current generator until migration verified |
| Multiple competing PNG, TXT and special-window exports | CONSOLIDATE | One export sheet with expert formats under Advanced |
| Five HUD skins (Striker/Ghost/Strip/Tactical/DUKO Lens) | RETIRE from new UI | Same text views, different wrapping/frames; nobody needs five unpolished choices |
| Four main app themes and ornate per-control styling | RETIRE from new UI | One coherent design system first, optional accessibility contrast later |
| Raw device capability dump in normal Settings | MOVE to Diagnostics | Engineer-oriented data shouldn't dominate everyday tasks |
| Experimental inaccessible SurfaceFlinger / privileged FPS probes | QUARANTINE or remove after dead-code check | Do not advertise unsupported FPS |
| Hardware tweaking / underclock and game control | OUT OF SCOPE | This app is telemetry-only; never silently gain root or device-control permissions |
| Hardware-specific battery runtime guess | NO unless validated usable capacity | Avoid bad extrapolation |

## New navigation (only four main destinations)

- **Overview:** single truthful status card (IDLE/READY/RECORDING/RECOVERING), Start/Stop, elapsed time, data age, overlay state; most recent session and Next Test.
- **Sessions:** searchable/filterable recordings, tags, one row click, one overflow menu, no four-button row. Session Detail: Summary -> Trends -> Events -> Technical Evidence as expandable sections; only meaningful findings in Summary.
- **Compare:** choose 2–5, check compatibility of game/track/driver/scale/sample quality, view A/B and A/A repeatability with uncertainty, then export **DUKO Comparison**. Do not choose "winner" when conditions are incompatible or FPS is unavailable.
- **Setup:** Test Setup profiles and HUD Studio; data backup/restore and permissions in separate subsections. Diagnostics under Advanced, not in primary navigation.

This is ONE cohesive design system, landscape/controller aware and touch friendly; portrait responsive where feasible. Optimize for handheld 16:9 and narrow-landscape layouts, real font scaling, large target sizes and safe insets.

## Visual language

**Working theme:** deep cobalt/slate surfaces, cool cyan focus, pale neutral text, warm amber for meaningful warnings, restrained red for recording state. No permanent shine animation, no faux glass that obscures games, no different color family per screen. Light/border hierarchy comes from semantics, whitespace and a consistent typography scale, not decorative corner strokes.

Use consistent component tokens for spacing, radius, typography, focus and buttons. Implement complete high-fidelity mockups of Overview, Sessions, Session Detail, Compare, Test Setup and HUD Studio **before** modifying production UI. Test those against the supplied 17 Nov screenshots.

## HUD 2.0: replace renderer, keep sensor/service backend

**First production HUD:** one compact glass capsule, 1 line of user-picked metrics with abbreviated but never ambiguous labels + a compact record/timer control; content-driven width clamped to safe visible area. Distinct standby `READY · tap REC` (no fake telemetry) and REC state. A clean optionally expanded two-row card can follow only after the capsule is approved, with a true locked/docked behavior. No need for Striker, Ghost, Tactical, DUKO Lens or full-screen Compose interceptors.

Mandatory constraints:
- Overlay always remains a single owned WindowManager root; separate 44–48dp touchable unlock affordance only when necessary. Touches outside the actual HUD must reach the game.
- Distinguish unavailable sensor `—` from 0; never wrap Celsius or W units independently. Suppress unsupported metrics instead of "No sources" occupying half a card.
- Docked badge must clearly indicate `REC` (with safe elapsed indicator) or `READY`; single unlabeled 44° is not acceptable.
- Offer 2–5 most useful metrics, ordered and previewed using faithful sample data. Do not poll new hardware for animations. Drag and opacity controls belong in HUD Studio; in-game chrome should be minimal.
- Locked mode protects gameplay touches and leaves a discoverable recovery control; dragging/locking must never trigger accidental STOP/REC.
- All rendering driven by immutable `HudRenderState` and same semantic design tokens as the app. Choose a lightweight native overlay host (View/Canvas) or experimentally verified ComposeView, but decide based on 60-minute CPU/PSS/touch testing, not aesthetic intuition.
- If system suppresses/detaches the overlay, status should indicate `not confirmed` rather than calling it visible just because the app requested it. Android can control overlay visibility.

## Recording + HUD + Quick Settings: one state contract

Do NOT represent one source of truth with unrelated volatile fields. Derive tile, app and overlay views from one transition-ordered snapshot:

```
Recording: IDLE | STARTING | RECORDING(sessionId) | STOPPING | RECOVERING | ERROR
Overlay:   OFF | REQUESTED | ATTACHING | SHOWN | HIDDEN_BY_USER |
           PAUSED_IN_APP | DOCKED | DETACHED | PERMISSION_MISSING
Health:    VALID | SAMPLE_GAP | RECOVERED | DATA_STALE | SOURCE_UNAVAILABLE
```

The state store should expose one immutable snapshot with sequence/version and timestamp. Events (ARM, START, STOP, HIDE, SHOW, LOCK, UNLOCK, APP_FOREGROUND, PROCESS_RECOVERY) go through a single coordinator; forbid a second REC when one is active or recoverable. Main Activity and QS are readers/controllers of the same state, not separate interpretations.

| Actual state | Record tile | Overlay tile |
| --- | --- | --- |
| Idle / overlay off | Inactive, Start | Inactive, Show |
| Standby and overlay physically shown | Inactive, Start | Active, Hide |
| Recording and overlay shown | Active, Stop | Active, Hide |
| Recording with overlay hidden | Active, Stop | Inactive, Show |
| Recording, app intentionally pauses overlay | Active, Stop | Inactive, Paused in app |
| Recovery pending | Inactive/indeterminate, Recover | Inactive/indeterminate, Reconnect |
| Overlay permission missing or attach failure | Recorder independent | Inactive, Setup / Unavailable |

No tile may claim active HUD based solely on requested+attached. Update state on acknowledgment from the WindowManager/Service, then refresh Quick Settings. Report any uncertainty honestly.

## Architecture: rebuild UI shell, not telemetry algorithms

```
:app-shell (Compose navigation, screens, theme, accessibility)
    -> :presentation (state holders / ViewModels / immutable UiModels)
    -> :domain (session commands, status reducer, use cases)
    -> :data (existing SQLite v9 repository adapter, backup)
    -> :analytics (reuse and validate pure JVM math)
    -> :monitor (DirectSampler + persisted session recorder)
    -> :overlay (single WindowManager host + lightweight renderer)
    -> :reports (DUKO comparison only in user journey)
```

Modules may initially be package boundaries within one Gradle app, then physical Gradle modules once the dependency graph is green; don't break release signing or Android process boundaries gratuitously. Keep telemetry pure/read-only. Android recommends unidirectional UI data flow, thin composables and ViewModel-owned screen state.

Migrate the current `MainActivity` (~193 KB), `RecorderHud` (~72 KB), `RecordingService` (~45 KB) and `SessionStore` (~53 KB) by extraction and adapter layers. Do not paste or expand these monoliths into the new shell. Use contract/fixture tests for old/new parity.

## Branding and data safety

CoreMetrics remains the historical development package until we have data migration and test coverage. New display name and repository are separate decisions:
1. Pick a distinctive final brand only after Play Store, GitHub, domain and trademark review. The current names `CoreMetrics`, `PulseDeck`, `TraceDeck` and even `DUKO` have visible third-party uses; do not assume exclusivity.
2. DUKO remains the comparison/report subbrand unless we explicitly revisit it.
3. Changing Android `applicationId` creates a DIFFERENT app from Android's perspective; SQLite and preferences won't silently migrate. Keep package ID stable throughout shell development or provide a user-tested portable ZIP export/import and migration protocol before public rebranding.
4. Preserve the prior repo/branch history, original database and archive formats, and verify the release signing plan; never destroy user sessions as part of a visual redesign.
5. Audit third-party dependencies, licenses and current Play requirements before release.

## Phased delivery with exit criteria

**Gate 0: frozen baseline.**
- Fix failing version assertions and any other CI regressions. Run JVM, optimizedTest, API 33 instrumentation. Snapshot app behavior, statuses, exports and screenshots.
- Archive a few real sessions + test fixtures. Validate 2s/5s with Odin and confirm data recovery and no duplicate overlay. This is a starting point, not a claim that v0.9.9.9 is stable.

**Gate 1: design proof (no production renderer replacement).**
- Wireflows for all four destinations and two HUD concepts at actual 1920×1080 game screenshots.
- Decide one primary HUD, approve typography, dimensions, touch and meaning of every button.
- Error/empty/long-label conditions, large font, controller D-pad, safe insets.

**Gate 2: state reliability.**
- Implement reducer + immutable snapshot + process ownership contract; app/HUD/QS tile truth table integration tests.
- Kill/restart service while in Eden; recover same session ID; zero duplicate overlays, no false active tile. Detect and report reconnection.
- Track RAM and process deaths; avoid unnecessary standby sampling.

**Gate 3: new UI shell.**
- Overview, Sessions, Session Detail, Compare, Setup; single coherent theme, test metadata editor and humanized duplicate-name errors.
- Remove old screen entry points only when replacement passes replayed data tests.

**Gate 4: HUD implementation.**
- Build approved compact HUD with one owner. Test attached/detached, overlay permission, locked/docked, keyboard/system overlays and all screen rotations/safe areas.
- 30 repeated REC/STOP, hide/show, process restart, Quick Settings toggles; service and tile states must match. Actual device screenshot comparisons, no UI clipping.

**Gate 5: analysis/report integration.**
- Session details focus on evidence-qualified findings; 2–5 session DUKO comparison stays flagship.
- Preserve raw CSV/ZIP as secondary export; no fresh report version format unless a change is necessary and tested.

**Gate 6: launch candidate.**
- OptimizedTest 60-minute Eden gaming test, sustained PSS/CPU/GC/tick measurements by process, interactive jank, all old sessions readable, battery-power and sensor provenance, store listing checks and new-brand migration plan.
- No claim of Android-process-kill immunity. Recovery must be safe, visible and accurate when the OS still kills a process.

## What to measure rather than assume

- v0.9.9.6 one-shot UI PSS was 101 MiB; this is not enough to compute total app cost with :monitor. Measure both process PSS under comparable hardware/OS conditions before setting budgets.
- No true game FPS in CoreMetrics: emulator's magenta FPS/FT display in screenshots is external and cannot be relabelled as CoreMetrics telemetry.
- System CPU/GPU utilization and battery-terminal power describe device behavior, not causal game performance improvement.
- Event detection and A/B reports must support "insufficient comparability" as a first-class conclusion.
- Short recordings with restarts may still have no detected gaps >15 s; distinguish data coverage from process uptime.

**Decision request for design phase:** Adopt hybrid rebuild, one coherent main theme, one carefully designed compact HUD at launch, and DUKO comparison only. Rebranding before public release, not before safe data migration.
