# CoreMetrics v0.8.8 — Odin 2 Portal A/B comparison acceptance

This is a physical-device validation checklist. GitHub CI passing is NOT proof of gameplay compatibility, comparable conditions, FPS validity or device safety.

## Installation and preservation
- [ ] Install v0.8.8 Frosted APK (app-v088base.apk, package com.locutus5382.coremetrics.v088base) side-by-side with v0.8.7. Confirm existing v0.8.7 sessions remain untouched; import CSVs into v0.8.8 for analysis.
- [ ] Verify the Frosted-only APK has no optional theme picker. Themes APK has isolated app data and retains old optional finishes.
- [ ] Verify no new permission or change to gameplay REC/STOP, standby, overlay, HUD lock, polling rate or recorder notification.

## Select comparable recordings
- [ ] Record two separate runs of the same game and same repeatable scene or import previous CSVs. Record graphics-driver/render-scale metadata and configuration labels. Start temperatures, fan state and power source should be noted manually.
- [ ] Use one long (5–10 min) and one short (1–3 min) recording. In Compare, select A and B; shared overlap must stop at the shorter actual duration, not an artificially filtered start.
- [ ] Check default shared overlap, 30s warm-up preset, custom thumb range and 5s nudge buttons using touch and Odin controller. A/B card, chart range shading and export window must stay aligned.
- [ ] Toggle "Show unmatched full timelines". The longer chart tail may become visible but must NEVER change the A/B card, delta signs or exported window.
- [ ] Toggle chart GPU >=15% filter; chart trace may change, but summary statistics and A/B report MUST stay unchanged. If all selected points are filtered from chart, a clear explanation must show.

## Numbers / provenance
- [ ] Pick 15–80s and independently calculate A/B medians, P95 and B−A from source timestamps. Compare the numbers to the scorecard. CPU/GPU load use median%; power uses time-weighted integrated valid discharge in W.
- [ ] Confirm observed P3/P7 caps reflect selected recording windows, are not inferred from profile labels and display "unavailable" if missing.
- [ ] Confirm the reference start for both timelines is each ORIGINAL first sample after REC. When warm-up or GPU filtering removes initial points, 50s still means 50s in each original source.
- [ ] Confirm missing power, charging, gaps or stale metadata cause caution text or "unavailable"; no zero substitution, imaginary FPS or promised throttling diagnosis.
- [ ] Record deliberately different GPU workloads. The A/B summary must issue a GPU difference warning. An interrupted run must also show a warning. Even no warnings should say conditions remain unverified.

## Export
- [ ] Select a nondefault crop and export "Selected A/B report". The saved TXT should state the two session IDs, raw window 15–80s, source sample counts, original status, metadata, observed limits, median/P95 and discharge coverage, and all comparability cautions.
- [ ] Confirm full/unmatched timeline switch or GPU-only chart filter cannot alter TXT values. Verify no source DB rows were modified.
- [ ] Select a too-short window (<6 samples in A or B); export should disable and status should be INSUFFICIENT DATA.
- [ ] Cancel the Android document picker; no side effects. On storage failure show a readable error.

## Regression / release gate
- [ ] Sessions → Analyze v0.8.7 still supports custom single-session crop, stability table and standalone TXT/CSV exports.
- [ ] Compare existing technical evidence, regime segments, manual links, and single-session report exports still work.
- [ ] Check 2s and 5s sampling intervals, missing load, mixed power, >15s timestamp gaps and short recordings. Charts must not paint falsely continuous data.
- [ ] Confirm controller focus and horizontal-scroll behavior in Odin 2 Portal landscape; no delayed scrolling or unexpected activity destruction.
- [ ] GitHub Actions: testDebugUnitTest + assembleDebug + assembleV088base + assembleV088themes succeed, with both artifacts attached to latest head SHA.
