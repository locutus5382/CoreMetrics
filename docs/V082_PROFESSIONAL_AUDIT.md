# CoreMetrics v0.8.2 — independent professional engineering assessment

**Scope:** Manual review of the GitHub Android/Kotlin source on the v0.8.2 maintenance branch, Gemini's attached v0.8.1 performance audit, and available JVM unit-test / Android debug build CI. **Not** a physical Odin 2 Portal acceptance test, a measured energy/performance benchmark, a security penetration test, or Google Play approval. Scores below are *engineering judgments*, not certification.

## Executive verdict
**Overall engineering maturity: 6.8/10 — well-structured observational prototype, not yet production-ready.**
**Release readiness: 4.2/10 — device survival and Play policy gates remain unresolved.**

### Scorecard (0–10)

| Discipline | Score | Evidence / limits |
|---|---:|---|
| Telemetry provenance and measurement honesty | 8.2 | Raw SQLite and source statuses retained; missing readings stay unavailable; CPU ceilings are observed not authenticated; frame FPS remains off |
| Analytics correctness | 7.5 | Discharge-only gap-aware integrated Wh, normalized mWh/min, time-weighted W; manual linked gaps retained. Comparability still limited by workload and sample cadence |
| Runtime efficiency | 7.7 | One sticky battery lookup, cached regex/policy path discovery, reduced HUD blink work, keyed Compose memoization. No Android Profiler measurements yet |
| Architecture / maintainability | 6.0 | Separated telemetry, storage and analytics, but MainActivity remains roughly 1,500 lines and RecordingService contains UI/window/storage lifecycle orchestration |
| Low-memory resilience | 5.2 | Bounded user-initiated foreground-service lease preserves ID and gaps if Android restarts it; previous Portal LMK kills remain a documented observed issue |
| Automated testing | 5.9 | JVM tests cover energy, parser, regimes, profile conflicts and CSV logic; no device instrumentation for WindowManager overlay, real SQLite migration or emulator launch/kill scenarios |
| Security and privacy posture | 8.3 | No INTERNET permission, read-only sysfs telemetry, no hidden root/PServer, non-exported recorder/provider, user-visible service/notifications. Overlay and usage-access permissions deserve careful UX |
| User interface / accessibility | 7.2 | Stable Frosted default, optional themed fork, manual labels, controller pathway. Real Portal landscape focus, locked chip hit target and readability unverified |
| Distribution / Google Play | 4.0 | Two debug APKs, no signed production AAB, FGS specialUse policy declaration not approved, source branches remain stacked and unmerged |

**Scoring method:** Qualitative assessment with emphasis on correctness, resilience, maintainability and evidence. Debug build compilation and green JVM tests are necessary but do not substitute for device testing.

## Detailed validation of Gemini's eight ideas

1. **Duplicate sticky-battery read — confirmed and fixed.** DirectSampler previously called Context.registerReceiver(null, ACTION_BATTERY_CHANGED), then BatteryPowerReader independently called it. Both now interpret one fetched Intent per sampler tick. Standalone BatteryPowerReader.read() still works for other clients.
2. **Regex and directory allocations — confirmed and fixed, with guard.** Regexes in DirectSampler/CpuMath/FrameLatencyParser/V08Analytics are cached. CPU policy *paths* are cached after successful discovery, but an initially empty scan is retried; frequencies and ceilings remain read fresh. Full cpufreq topology changes after successful discovery aren't automatically rediscovered.
3. **HUD blink callback waste — confirmed and fixed.** No 650 ms callback while docked or blinking disabled; a single scheduler synchronizes transitions. Reuses dock drawable.
4. **Repeated Compose panel analysis — confirmed and fixed.** remember keys include data or labels, so new points invalidate cache. Keys are not a substitute for immutable data ownership: future mutable in-place list updates would require review.
5. **A/B filtered-list and mean recomputation — confirmed and fixed.** Keyed filters and pure per-sample summaries; discharging-only sample-mean power. Compare report's integrated energy remains separate and time-weighted.
6. **One-sided Live profile warning — confirmed and fixed.** Same pure review predicate used by Live, Observed Profile and benchmark quality. Generic 'Underclocking' with stock CPU ceilings means 'check settings,' not proof of a failed GPU/CPU tuner.
7. **Warm-up cards inconsistent — confirmed and fixed.** Session detail and Compare use filtered sample views; absolute timestamps, original elapsed origin and linked recording gaps remain intact. First 30 s means after *first recorded sample*, not verified game-world load.
8. **Unused legacy UI — confirmed and removed.** CompareChart, InstrumentBackdrop and unused scrubFraction are gone.

## Highest priority remaining gates

**P1: Prove recorder lifecycle under Android memory pressure.** Repeat game-first and HUD-first Eden startup; capture ApplicationExitInfo and the recorder journal; verify that the same session ID resumes only with a bounded lease, genuine time gap, and no unauthorized automatic restart after STOP. START_STICKY is best-effort; no guarantee against OEM LMK.

**P1: Prove user escape from the HUD lock.** Test the independent 48dp unlock chip while the main window is FLAG_NOT_TOUCHABLE, near both screen edges, docked, during standby/REC, and with a foreground-notification unlock fallback. Do not defeat Android untrusted-overlay touch rules.

**P1: Validate data continuity and upgrades on-device.** Import old 26-column CSV and new 28-column CSV; export/import roundtrip; test SQLite migration v1/v2/v3→v4 with copies of user data and interrupted recording recovery. Two parallel package variants have separate databases.

**P1: Android/Google Play release policy.** Validate target SDK 36 and `specialUse` FGS type/declaration against current Play policy, user permissions, Data Safety, signed AAB and privacy materials. A debug APK is not a production release.

**P2: Reduce MainActivity/RecordingService coupling.** Split state holders/view models and navigate screens with independent UI components; separate overlay lifecycle state machine from the recording loop and persist explicit service state.

**P2: Instrument on real hardware.** Measure actual process PSS, GC frequency, per-sample duration, dropped samples, overlay frame cost and battery drain with and without HUD, in Standby, during Eden launch and in active recording. Claims of measurable performance savings cannot be made from source changes alone.

**P2: Keep experimental FPS isolated.** No proven user-permitted PServer/SurfaceFlinger timestamp path; avoid displaying layer-present rate as emulator render FPS or generating false 1% lows. A dormant parser/probe is not a validated data source.

## Suggested acceptance before tagging a release candidate

- Final-SHA CI: JVM unit tests + debug + v0.8.2 Analytics and Themes APK builds succeed.
- Physical device: 10+ repeated lock/unlock transitions, REC/STOP, HUD docking/expanding, L1/R1 + D-pad, game-first vs HUD-first launch and at least one Android LMK/recovery scenario.
- Data: no lost or fabricated telemetry across an interruption, consistent warm-up exclusion, A/B power calculations with charging/discharging mixtures, old/new CSV import and database upgrades.
- Performance: compare sample acquisition latency, CPU duty cycle, RSS/PSS and number of blink callbacks with the v0.8.1 baseline. No performance claim until measured.
- Release: remain a draft PR until these gates pass, then prepare a signed release and compliance review.

**Audit conclusion:** v0.8.2 is appropriate as a **testable alpha**. It is not appropriate to call it a release candidate or merge all stacked PRs into production solely because CI is green.
