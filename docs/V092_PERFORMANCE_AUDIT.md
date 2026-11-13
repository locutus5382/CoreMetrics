# CoreMetrics v0.9.2 — Odin 2 Portal and Android performance acceptance

**Build provenance:** Verify both APKs and \`assembleDebugAndroidTest\` match the exact final commit; the production user app remains unchanged by installing these side-by-side suffix packages. Export your older sessions first.

## A. Correctness before claiming speed
- [ ] Import *the same* 2s or 5s CSV into v0.9.1 and v0.9.2. Source row counts, source timestamps, CPU/GPU P50/P95, power coverage and B−A deltas must match.
- [ ] A 30+ minute source recording with long gaps, missing sensors and short temperature spikes: draw Classic, Advanced and A/B charts. Peaks and valleys must remain visible, and broken telemetry must not be visually connected.
- [ ] Move cursor precisely to a measured sample near the edge of a gap; it must show the real value. Move it to the middle of the missing interval; show Unavailable, **not** a fabricated interpolated temperature.
- [ ] Zoom/resize, change theme and switch between Thermal / CPU clocks / Core balance / GPU / Power. No stale geometry, clipped peaks or unexpectedly flat traces.
- [ ] Export the same selected segment to TXT, CSV and PNG on both versions. Pixel reduction is display-only and must not modify calculations or exports.

## B. Quantified interaction performance (compare v0.9.1 vs v0.9.2)
- [ ] Use the same Odin firmware, screen refresh/resolution, power mode, game session CSV, theme and app screen on both variants.
- [ ] Record a 30-second Perfetto / Android Studio System Trace while continuously dragging the Advanced timeline cursor, then repeat for Classic and A/B; compare UI-thread frame time and janky frames (50th/95th percentiles, missed-frame count).
- [ ] Measure Java/Kotlin allocations and heap after 20 cursor drags; compare v0.9.1 vs v0.9.2. Observed frame-time and RAM results must be reported, not guessed.
- [ ] Check 2s and 5s cadence sessions and 5k–12k sample sessions if available; new chart work should scale with screen width rather than source row count. Exported PNG may still process all original rows by design.
- [ ] Repeat rapid theme/page changes for 3 minutes. No ANR, stale state, permanently busy spinner or unexpected spikes in process RSS.

## C. Lifecycle and recorder safeguards
- [ ] Open CoreMetrics without starting REC/HUD, navigate Live → Sessions → Settings → Live; note idle app CPU usage and \`:monitor\` process behavior. v0.9.2 backs off visible idle polls but a status-provider query can still launch the monitor process.
- [ ] Start REC from Live, immediately enter Eden, switch back and forth; in-app HUD must pause over CoreMetrics and return to the game. Test quick backgrounding immediately after START (before the next status poll).
- [ ] Arm HUD standby, lock, dock/expand, hide/show, start REC, stop and explicitly DISARM. No orphan unlock control or unintended auto-start.
- [ ] While a session is recording, recreate Activity / switch app. Recording and database must persist. Returning to Sessions must reload accurate rows. No database-is-closed exceptions.
- [ ] Trigger real-world Android low-memory pressure if safely reproducible; check Recorder Diagnostics. START_STICKY does not guarantee OEM process recovery; gaps are never filled with synthetic samples.
- [ ] Test idle/stopped app permission denial (POST_NOTIFICATIONS / overlay / Usage Access), and cold-launch without permission; no crash or unexpected sampling.

## D. Android instrumentation
- [ ] On an emulator/test device with NO valuable ongoing session, execute \`gradle :app:connectedDebugAndroidTest\` or equivalent Android Studio instrumentation run.
- [ ] Confirm all four PNGs decode at 1600×1050, CreateDocument MIME is image/png, and MainActivity recreation is crash-free.
- [ ] Exercise actual SAF save/share/cancel and provider failure manually; the compiled instrumentation checks are NOT a replacement for a real document provider or share target test.
- [ ] Use a second Android device/API version to verify supported telemetry degrades to Unavailable when missing.

## E. RC1 go/no-go
- [ ] Final v0.9.2 SHA: JVM unit tests pass, debug + Frosted + Themes APKs assemble, Android instrumented test APK compiles, artifacts are present.
- [ ] No open P0/P1 crash, data corruption, false-completion state or misleading report calculation.
- [ ] Production signing, AAB, privacy/Play Console declarations, and human closed-beta acceptance remain OUTSIDE v0.9.2; see RC1_READINESS_PLAN.md.
