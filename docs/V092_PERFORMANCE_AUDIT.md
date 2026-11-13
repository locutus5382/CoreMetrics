# v0.9.2 — Performance design review and measurement protocol

## Changes implemented
1. **Gap-aware min/max bucket reduction:** \`analysis/ChartTraceReducer.kt\` converts dense *display-only* time series into first/min/max/last real recorded points per horizontal pixel column, preserving their chronological order and source timestamps. Long gaps, missing observations, and diagnostic segment changes force explicit path breaks. No interpolation, synthetic means or changes to the underlying SQLite data.
2. **Screen-width cache:** The Classic timeline, Advanced Diagnostic Explorer and A/B scrub chart generate reduced samples with Compose \`remember(..., width, selected metric)\`. Their full-series metrics still come from raw samples, and cursor labels still examine original readings.
3. **Binary-search cursor:** Advanced Diagnostic Explorer previously called \`readings.minByOrNull\` on every drag, O(N). \`DiagnosticTimeCursor.nearest\` does O(log N) search and refuses deep-in-gap lookups; \`CompareTimeCursor\` already used binary search and is preserved.
4. **Bounded P95 markers:** Advanced chart spike candidates are now cached per immutable diagnostic distribution instead of being filtered on every cursor draw.
5. **Lighter lifecycle:** No synchronous monitor status ContentProvider call inside Activity.onStart/onStop. Use last-observed active state; periodic status query remains off the UI thread. When actively recording/HUD standby, the visible-activity polling cadence remains ~1.3s; idle screens now ~4.5s.
6. **Database hygiene:** Close Activity-owned SQLite helper at onDestroy (recorder process owns a separate writer with WAL).
7. **Android instrumentation scaffolding:** Assemble and compile Android PNG decode/SAF contract/Activity recreation tests. The GitHub hosted runner does not provide an emulator in this workflow, so no physical Android behavior is claimed from CI alone.

## Complexity and caveats
- Prior O(N) diagnostic cursor scan is now O(log N). For dense charts with thousands of source rows, drawing now visits O(width) extrema/edge samples rather than every sample on each redraw. Rebuilding buckets is O(N) only when data, selected signal, or display width changes.
- In the pathological case of nearly every source point belonging to a unique gap/segment, the reducer preserves all break boundaries instead of erasing missing-data evidence. This intentionally does not guarantee an upper bound when every point is a separate continuity island.
- Chart Path construction still occurs in a Compose Canvas draw scope using reduced samples, so a change of cursor can still redraw O(width) segments. Full cached Android GPU paths, background view capture or risky Canvas pooling were not introduced without profiling evidence.
- A/B analysis, CSV/TXT/PNG exports, service recording, sampling intervals, power integration and session metadata are unmodified. Use a source CSV round trip to verify strict equality.
- v0.9.1 introduced a separate successful recorder integrity fix. We will not rewrite the foreground service in a visualization-only release; physical LMK/Android recovery testing is a pre-RC1 gate.
- The UI still polls status every 4.5s at idle. Because the provider lives in \`:monitor\`, an idle query can still bring the provider process up; no claim of entirely eliminating monitor startup. A process-independent status channel would require its own reliability and multiprocess tests, and is deferred if the overhead is not measured as material.

## Suggested profiling procedure
- With an Odin 2 Portal and an identical imported recording in the prior v0.9.1 and current v0.9.2 side-by-side APKs, use Android Studio Profiler, Perfetto or \`adb shell dumpsys gfxinfo <applicationId> framestats\` and \`adb shell dumpsys meminfo <applicationId>\` to compare scroll/cursor jank and private dirty memory. Replace \`<applicationId>\` with \`com.locutus5382.coremetrics.v091themes\` or \`.v092themes\`.
- Warm both builds in the same power state, controller layout, display resolution, and chosen theme; capture repeated 30s cursor drags. Report median/P95 UI frame time, >16.7ms frames, memory delta and any ANR or process restart.
- Do not extrapolate a performance speedup from JVM reducer tests; only device traces can establish user-visible gain or battery impact.

## Deliberately deferred until RC1 engineering gate
- A full ViewModel/repository rewrite of the ~2k-line MainActivity is unnecessary for a stabilizing internal beta and risks recorder/SAF regressions.
- Automated LMK simulations are not equivalent to actual Android/Odin process killing. Manually test service lifecycle via low-memory system diagnostics.
- Existing two Quick Settings tiles and in-game overlay architecture are intentionally retained.
