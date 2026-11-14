# v0.9.1 — Odin 2 Portal integrity acceptance

Install the **v0.9.1 Themes** or Frosted debug APK, not both recorders at the same time. Package IDs and local SQLite sessions are isolated from v0.9.0. Preserve original source CSV exports before changing a test app.

## P0 — Recorder truth & continuity
- [ ] Record at least 30 minutes in Eden; STOP using HUD, notification and QS. Every explicitly completed session is marked COMPLETED, its recorded samples remain.
- [ ] Force-stop/kill recorder process via an Android developer test while recording; verify no false COMPLETED outcome. If system requests restart, original session ID and recorded gaps are preserved; lack of restart must be documented, not treated as a pass.
- [ ] Repeat locked-HUD/memory-pressure test that previously triggered LOW_MEMORY; check Recorder Diagnostics log, HUD unlock chip, no orphan overlay.
- [ ] Switching back into CoreMetrics must not overlay its own HUD or change a hidden HUD preference.

## P0 — Data evidence
- [ ] Observe tied CPU cores; the peak utilization percent remains available while the unique winning core may show unavailable.
- [ ] Import a known-good older v0.9.0 CSV. Verify sample counts and session results remain consistent with source.
- [ ] Import CSV with repeated timestamp, decreasing timestamp, or implausible CPU (300°C)/GPU (120%) readings. Reject atomically, show a message, no partial session left.
- [ ] Import with genuinely missing CPU/GPU/power data; preserve unavailable values and never substitute zero.
- [ ] Compare selected warm-up exclusion where the first 30s are charging and later values discharging. Only selected period should define the power condition.
- [ ] Analyze interrupted session containing >30s unsampled gap; distinct observed clock-limit phases must remain separate, not fabricated as continuous.
- [ ] Compare 2s and 5s cadence sessions. A/B shows warning and B−A remains numerically consistent.

## UI/PNG regressions
- [ ] From the bottom of a long Sessions list tap Analyze; screen scrolls to analysis header.
- [ ] Select an interval completely inside a missing-data gap. It must show a specific error, not infinite progress.
- [ ] Standby HUD ready message shows two actual lines; recorded HUD metrics remain legible.
- [ ] TXT diagnostics uses 2-decimal or n/a values, not raw 15-place float or literal null.
- [ ] PNG displays °C, original selected time bounds, legitimate gaps and the same P50/P95 as TXT. Four themes remain readable.
- [ ] Open SAF PNG picker, rotate or trigger Activity recreation, save and share successfully if supported by provider.
- [ ] Test SAF cancellation, provider write failure/storage-full behavior; incomplete files should be deleted if provider permits. An honest warning is required if deletion is unsupported.
- [ ] Check on-device CPU/RAM and input latency during repeated PNG exports.

## CI/RC1 gate
- [ ] Final SHA unit tests + debug + v091base + v091themes APK assembly succeed, artifacts reflect that SHA.
- [ ] P0/P1 defects found in Odin tests are repaired before the next milestone.
- [ ] v0.9.2 performance profiling and instrumented testing, plus production signing/Play prerequisites, remain separate RC1 gates.
