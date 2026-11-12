# CoreMetrics v0.8.1 — Gemini audit acceptance tests

## Packages / source of truth
- These are **separate packages**. Install both alongside v0.8; do not uninstall old versions until exported data has been checked. `CoreMetrics-v0.8.1-analytics-only` contains `app-v081base.apk`; `CoreMetrics-v0.8.1-themes` contains `app-v081themes.apk`.
- Download from **the final branch SHA's successful workflow run**, not from a previous green commit. Build should pass `:app:testDebugUnitTest`, debug, Analytics and Themes assemblies.
- Only one of the CoreMetrics recording/HUD services should run at a time. CSV import/export and SQLite v4 remain unchanged.

## Four regression fixes
1. **Warm-up / regime chronology:** Import or record a session with observed P3/P7 ceilings that change in the first 30 seconds. Compare > Skip First 30s OFF should include the initial regime. ON should exclude all initial-window samples from Session A's *In-Session CPU Ceiling Regimes* as well as other A/B analysis, while preserving the original elapsed-time reference. A 20-second session ON should show no regime cards rather than fabricated numbers. Linked recordings must still show the original real time gap.
2. **Drawer touch-lock:** Arm and open the floating HUD settings drawer in Eden. Tap the cyan flat unlock-to-lock icon. The drawer must disappear; only the compact HUD stays visible and click-through. The **independent** solar locked chip must still unlock. Re-open and re-lock repeatedly, near left/right screen docks, and check fallback notification unlock. Android overlay security may still block touches; report exact device behavior.
3. **RAM and power state:** On Live, the 4th ring is RAM used in GiB; its arc uses Android's reported total RAM once, not a hard-coded 7.02 GiB. The fourth metric card displays Discharging, Plugged in, or Unknown, derived from explicit Android battery status. The HUD's experimental FPS reader remains disabled; do not label display Hz as game FPS. Test cold startup, live sampling and plugged charging.
4. **Profile mismatch / diagnostics:** Underclocking manually selected + all observed P3/P7 stock (2.80/3.19) should show review caution (CPU only; may be GPU underclocking). Stock selected + observed Medium/Large remains a mismatch. Unknown ceilings and Custom selection must not trigger a false mismatch. Open Diagnostics and verify heading **CoreMetrics v0.8.1 recorder diagnostics**.

## Feature preservation
5. Sessions remain Session N, with Stock / Underclocking / Custom and optional driver/resolution tags; no new tag or sample column.
6. v0.8.1 Analytics remains unchanged Frosted Glass. v0.8.1 Themes defaults to Frosted and permits Liquid/Tactical in Settings. HUD native WindowManager rendering, QS tile behaviors and recording service remain unchanged by theme.
7. Re-run game-first vs HUD-first Eden startup to diagnose the known Android low-memory termination; this patch **does not promise to solve OEM LMK**.
8. Only accept a release candidate after repeatable Portal testing of controller L1/R1, D-pad A/B scrubber, drawer touch-lock, Live screen RAM/power, CSV import, and both UI finishes. Do not merge the draft PR or publish before physical testing.
