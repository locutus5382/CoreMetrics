# CoreMetrics v0.8.6 — Odin 2 Portal acceptance checks

This is a **device verification checklist**, not a claim that v0.8.6 has already been validated. Both Android APKs are experimental. Start with **v0.8.6 Frosted**, using only one CoreMetrics recording overlay at a time.

## Installation and isolation
- [ ] Install v0.8.6 Frosted beside v0.8.5 Frosted, without uninstalling existing packages. It must show fixed original Frosted Glass and **not** expose other theme choices.
- [ ] Install v0.8.6 Themes if desired and confirm other finishes are optional. Both versions should store independent session histories.
- [ ] Check Android permissions and compare FPS/HUD/recording behavior against the previously working build. v0.8.6 should not add sampling or permissions.

## Single-session metrics
- [ ] Record at least 3 minutes of the same game at a 2s or 5s sample interval, unplugged; stop normally.
- [ ] Sessions -> Analyze: P50, P95, peak, final 60s median show for sensors that returned valid data; missing sensors show **—**, not 0.
- [ ] Move the synchronized cursor through the Thermal, CPU clocks, Core balance, GPU and Power tabs. P95 thresholds and peaks should align with source CSV peak timestamps, and 60s median lines should be smooth but never bridge a data gap.
- [ ] Switch P3/P7 in CPU clocks. Actual clocks and thermal readings must use the same recorded timestamps. Observed CPU frequency ceilings may be viewed separately under technical details; clocks dropping do not by themselves mean throttling.
- [ ] Expand technical diagnostics: the **Classic Signal Explorer** remains available, including RAM. Existing source and session report exports must remain functional.
- [ ] Run a 5-10 minute session: compare final 60s median, early 3min CPU slope and final 60s slope; expect noise from highest-of-multiple sensor sources.

## Power validity
- [ ] On a discharging recording longer than 120s with >=60% valid power coverage, confirm runtime is labeled approximate and based on a nominal 30.8 Wh assumption.
- [ ] Record while plugged in, and with mixed charging states. Battery-discharge statistics must **exclude** charging samples, and the remaining-time estimate must be unavailable.
- [ ] Compare reported time-weighted mean W and integrated Wh with the existing Benchmark Report / CSV. They should match because the new screen uses the previous validated energy calculation.

## Continuity and performance
- [ ] Cause a recording interruption or import a CSV with an obvious >15s timestamp gap. The chart must show a broken line; pressure duration, 60s medians and temperature regression must not reconstruct the gap.
- [ ] Test 1 minute, 10 minutes and a longer session. Opening the analysis screen should not block recording or unexpectedly increase gameplay telemetry polling.
- [ ] Save a single-session plain-text report and confirm the appended **ADVANCED SINGLE-SESSION DIAGNOSTICS** section. A/B comparison should be unaffected.
- [ ] Compare two runs with documented game scene, driver, emulator, render scale, device power and thermal-start conditions. The diagnostics **describe** these runs; they do not prove causal differences.

## Interpretation limits
- Busiest-core fraction is **not** an actual per-core residency history and does not confirm single-thread-limited gameplay.
- CPU and GPU thermal readings use accessible, selected sensor nodes, not guaranteed package/socket temperature.
- Observed frequency-cap transitions do not identify which application imposed the limit.
- FPS is not fabricated when Android's provider cannot deliver a verified game-frame stream.
- Battery-temperature difference is not chemical battery health or direct enclosure surface temperature.
