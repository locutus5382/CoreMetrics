# CoreMetrics v0.9.8.3 — Measurement Integrity & Reproducibility

**Development alpha**, versionCode 42, `0.9.8.3-measurement-reproducibility-alpha`.
Based on v0.9.8.2. Do NOT merge before the bulk Odin test and Android
instrumented archive/SQLite tests have actually EXECUTED.

## Implemented changes

### A. Battery-terminal power provenance
- The existing `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` current and
  `ACTION_BATTERY_CHANGED.EXTRA_VOLTAGE` voltage remain primary sources.
  Their recorded `battery_power_source` is now explicit about BOTH origins
  and whether update ages are verifiably synchronized (they are not).
- Read-only sysfs current/voltage are fallback only if an Android reading is
  unavailable. No additional sysfs read during an otherwise valid tick.
- A substantial polarity contradiction between current and Android's reported
  charging state invalidates the watt reading, marks
  `CURRENT_SIGN_STATUS_CONFLICT` and excludes it from battery-discharge
  comparisons. A near-zero ripple is tolerated.
- Battery-terminal W is NOT SoC-only power, USB/wall input power or
  measured game performance.
- The source is exposed to A/B and A/A analyses. Changed provenance is a
  caution. No unverified claim of a superior voltage source is introduced.

### B. Device capability diagnostics (on request only)
- Dynamic CPU policy enumeration from `/sys/devices/system/cpu/cpufreq`,
  numeric bounded sorting, max 16 policies, readable `time_in_state` data.
  Values are cumulative **residency counters**, not CPU thread scheduling.
- One-shot Android battery current/charge/energy counter availability and
  read-only battery sysfs raw values. Units: uA, uAh, nWh; vendor update
  cadence and correctness remain unvalidated until tested.
- Basic device model/API information recorded for provenance. No per-tick
  counter queries, new permission, root or vendor-specific thresholds.

### C. Conservative repeated-run A/A comparison
- Compare → `Analyze repeated test runs (2–5)` (collapsed by default).
  Repetitions require the SAME manually saved Quick Test Profile name.
- User selects 2–5 runs. Analysis reads full preview data on demand and
  refuses a truncated >12k run, not silently compare the first chunk.
- Compare the **median of per-run medians** and the observed minimum–maximum
  range for CPU/GPU temperature, GPU load and eligible discharging battery
  terminal W. Repetitions with insufficient power coverage remain missing,
  not 0 W.
- Warn on different or missing driver, resolution, power/fan/ClusterTune,
  game/track notes and battery source; metadata are user statements, not
  verified device settings. Still no verified workload equivalence.
- NO statistical significance, confidence interval, FPS, driver winner
  or causal conclusion from only two–five runs. Retain existing A/B studio.

### D. Long-session CSV round-trip
- Existing CSV export is fully streaming. **CSV import now accepts at most
  100,000 samples per session** (previously 12,000); every row is inserted
  within one rollback-capable SQLite transaction. No in-memory CSV accumulation.
- The on-screen analysis preview still has a separate 12,000-row ceiling;
  it must continue showing INCOMPLETE and disabling partial segment exports.
- Never claim to have analyzed all samples from a long session until a
  future paged/incremental analyzer validates it.

### E. Portable additive archive (experimental)
- Settings → Portable archive: export a versioned ZIP with `manifest.json`
  followed by complete, importable CSV entries for each session.
- Includes metadata, Quick Test Profiles and manual A/B associations.
  User's recording/HUD defaults are included in the archive manifest for
  reference, but **not activated** on another Android device on restore.
- Restore appends NEW sessions, presets and remapped links. It never deletes
  or overwrites existing sessions. A duplicate preset name receives a
  distinct restored name; historical snapshots remain unchanged.
- Explicit limits: 200 sessions, 200 presets, 2500 links, 250k total
  samples, <=100k per session, <=800KB JSON manifest.
  Exceeding limits aborts rather than silently omitting data.
- Restore checks ZIP entry names/order, CRC, sample counts and manifest
  version, and uses one outer SQL transaction. A corrupt archive must
  roll back ALL newly imported sessions and links.
- IMPORTANT: a successful **CI build only assembles** Android instrumentation;
  it does not execute it. Verify restore + rollback on Android and a spare
  data set before relying on the ZIP as the only copy of sessions.

## Test matrix

1. On a test installation, select a stored profile, record at both 2s and
   5s via Quick Settings, verify correct session metadata and BAT T HUD.
2. Test battery source against raw voltage/current from one-shot Diagnostics.
   Record one plugged and one unplugged run; power classification must not
   describe charge-input data as discharge efficiency.
3. Use 2–5 same-profile recorded sessions; try renamed driver, fan changed,
   unknown render scale, unavailable watts, missing battery power source.
4. Import a 12,025-row export in a scratch test DB, verify stored count and
   complete streaming CSV re-export; check that Studio warns when only
   the first 12k are loaded. Confirm cancellation and atomic rollback.
5. Export archive, import into a separate scratch database; ensure sample
   counts, tags, profiles and A/B links survive with different new IDs.
   Restore same archive twice: no prior session is overwritten.
6. Corrupt/missing ZIP second entry, invalid manifest, invalid driver label:
   import must roll back the first entry and preserve all existing sessions.
7. Compare process memory and RECORDER_TIMING collector/DB/HUD/total P95
   with v0.9.8.2 at 2s and 5s. Battery source verification must not add
   new regular sysfs reads where Android current and voltage are valid.
8. Test READ-only capability probe on Odin 2 Portal; only mark counters
   *validated* after observing changing values and credible units across
   known conditions. No permanent CPU/GPU safety thresholds.
9. Check compact HUD, standby, lock, QS and interrupted REC restoration;
   no new monitoring service, background loop or animation was added.

## Deferred
- True full-session >12k analysis pagination/streaming aggregations;
  currently explicitly incomplete in the UI.
- Counter-based battery energy validation as an actual recorded signal;
  diagnostic reads alone do not establish trust or utility.
- DUKO individual and combined PNG/PDF reports (v0.9.9).
- Final HUD design themes and optional alternate HUD layouts (v0.10.0).
