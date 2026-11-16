# CoreMetrics v0.9.9.0 — Device & Measurement Foundation

**Development alpha · versionCode 43 · SQLite schema v9.**
Based on v0.9.8.3 without changing the foreground recorder's 2s/5s
cadence, database sample write interval, Quick Settings, HUD touch ownership
or the experimental ZIP restore semantics. **No DUKO PNG/PDF yet.**

## User-visible behavior

- No unconditional 30.8 Wh battery size assumption. The advanced single-
  session diagnostics and CPU-cap segment report now return runtime
  **unavailable**, even if discharge watts and battery percentage are known.
  This is intentional and scientifically more defensible than a guessed
  runtime on a different handheld.
- Settings → **Device telemetry compatibility** → Check this device runs an
  *on-demand* read-only probe on a background dispatcher. It reports Android
  model/API, available cpufreq policy names and related CPU IDs, KGSL/Mali
  readable GPU paths, battery counters and plausible design-energy
  candidates. A readable value is NOT treated as certified, calibrated,
  safe operating temperature or verified usable battery Wh.
- No restricted Android `com.android.internal.os.PowerProfile` reflection,
  root, Shizuku or third-party telemetry dependency has been introduced.

## Implementation

### Dynamic CPU policies
- Live monitoring now discovers policy directories rather than hardcoding
  policy0, policy3, policy7. The cap snapshots include the explicit
  `CPU_POLICIES:` marker. The analyzer recognizes all readable policy IDs
  and labels these as **numeric policy indices**, not "Prime"/"Efficiency".
- If topology is unreadable or >6 policies are present, the cap signature
  is INCOMPLETE and must not become a stock/underclock diagnosis.
- Old recordings without `CPU_POLICIES:` retain their legacy P3/P7 keys.
  Reference signatures are only reference-like measurements; they cannot
  prove what application set an underclock or why a ceiling changed.

### GPU adapters
- Conservative KGSL percentage/ratio and Mali percentage readers plus
  optional generic GPU devfreq **frequency only** sources.
- Only strict 0..100 integer or percentage inputs are accepted as load;
  a clock frequency is never silently reinterpreted as utilization.
- Read paths are cached when working. Unavailable vendor paths are retried
  at a bounded interval rather than on every recorder tick.
- Source tags explicitly include `READABLE_NOT_VALIDATED`; constant GPU
  clocks or unknown counters are not evidence of dynamic GPU scaling.
- Missing source → nullable GPU load/clock + `UNAVAILABLE`, not 0.

### Battery capacity and reports
- `energy_full_design` (microWh) is a one-shot unverified candidate.
  `charge_full_design` (microAh) is only converted when a separate
  **nominal** pack voltage value is present. `voltage_min_design` is
  NEVER substituted for nominal voltage.
- Design values are not measured usable capacity. Runtime projections stay
  disabled until a verified usable Wh mechanism and provenance are defined.
- Time-integrated battery discharge Wh and source-qualified terminal W
  remain available where the stored data support them.

### Origin/provenance persistence
- SQLite v9 adds nullable
  `sessions.device_manufacturer/device_model/device_code/android_api` and
  `samples.gpu_source`. New sessions copy Android Build identity at REC.
- CSV adds these optional fields at its END, keeping old header-mapped CSV
  imports compatible. The portable ZIP manifest and additive restoration
  carry the ORIGINAL recording-device identity; restore never replaces it
  with the Android device that opens the file.
- Existing A/B and A/A reports warn on differing/missing device identity,
  and differing/missing GPU telemetry paths. GPU paths are readable, not
  necessarily calibrated across ROMs.

## Test acceptance before merging

1. CI JVM tests (including source parsers and old report compatibility)
   pass and Debug, R8 optimized and AndroidTest APKs assemble.
2. **Execute** instrumentation v8→v9 migration on an actual Android
   device or emulator; preserve v8 sessions/samples/settings/profiles.
3. CSV round-trip with a fake recorded Mali device retains all source
   fields and Android API, including nulls on earlier CSV files.
4. Portable archive/restore on a scratch database preserves original
   device model, source labels, associations and all sample counts.
5. Odin 2 Portal: verify dynamically detected CPU policies correspond to
   readable core groups; no false tuner attribution and no crash.
6. A non-Qualcomm/unknown GPU must not fabricate 0%, 401 MHz or a GPU
   frequency. Test Mali with validated sample fixtures and on real
   firmware when available; absence is acceptable.
7. Device compatibility probe works without root and does not run during
   standby or a recording tick.
8. Compare recorder timing P95 and memory against v0.9.8.3 at both sample
   intervals; no extra per-tick battery design probes.
9. REC/STOP, QS, HUD lock and process recovery remain unchanged, including
   Android 15 foreground-service limitations when devices are available.
10. Verify report TXT/CSV and eventual DUKO metadata never claim that
    lower GPU load or battery W is equivalent to higher game FPS.

## Deferred to v0.9.9.1–.3
- DUKO Session Insight PNG/PDF, glossary and full-session streaming report.
- DUKO Comparison Report, selected baseline and up-to-five-session charts.
- Vendor-specific GPU and thermal-zone validation on Odin 3, Retroid and
  Mali firmware; UI portrait refinement remains v0.10.0.
