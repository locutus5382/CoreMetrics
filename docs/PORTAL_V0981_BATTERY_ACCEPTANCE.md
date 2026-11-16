# CoreMetrics v0.9.8.1 — Optional battery-temperature HUD acceptance

Build: versionCode 40; `0.9.8.1-battery-hud-alpha`.
Scope: battery-temperature presentation and a separate live Android-reported
battery-health OVERHEAT signal. The recorder, SQL schema v7, frequency-cap
analysis, Quick Test Profiles, CSV and DUKO planning remain unchanged.

## User-approved BAT T behavior
- HUD Studio > Visible Telemetry > **Battery °C** is OFF by default.
- Enable it to display `BAT T 35.2°C` from the existing recorder sample.
  The independent `BAT 4.2 W` power option stays unchanged.
- **NORMAL:** below 39°C, plain white text, absolutely no outline.
- **WARM:** >=39°C, thin **yellow 1dp rounded outline** around only BAT T.
- **HOT:** >42°C continuously for >=15 real elapsed seconds, thin **orange
  1dp rounded outline**, with orange text. At exactly 42.0°C WARM remains.
- HOT -> WARM only when temperature is **below 41°C for >=10 seconds**.
- WARM -> NORMAL only when **below 38°C for >=10 seconds**.
- Normal/Warm/Hot use identical reserved text width; toggling warnings
  should not push the lock chip or cause automatic docking/collapse.
- Missing/invalid temperature displays `BAT T —` with neutral text and no
  outline. An interrupted or missing measurement resets pending *transition*
  timers; it must never be interpreted as a normal/safe temperature.
- These are CoreMetrics *warmth advisories*, NOT vendor-certified device
  critical limits or direct evidence of throttling.

## Separate Android-reported warning
- Recorder already fetches one sticky `ACTION_BATTERY_CHANGED` Intent per
  sample. It now also interprets `BatteryManager.EXTRA_HEALTH`.
- `BATTERY_HEALTH_OVERHEAT` triggers a static red 4dp OS/device warning
  accent and accessible description. This is independent of BAT T selection
  and is NOT the same as the orange app-defined HOT outline.
- Unknown/missing health is null, not an invented healthy/normal status.
- This new battery-health Boolean is HUD-only in this iteration; it is NOT
  saved to SQLite/CSV. Historical BAT T values are already stored.
- Existing Android OS thermal status/headroom accent continues unchanged.
  CPU/GPU numeric colors stay advisory and do NOT imply critical limits.

## Odin 2 Portal validation
1. Backup recordings before installation if signing keys differ.
2. Install DEBUG initially. Compare with v0.9.8 at same 2s/5s sampling.
3. Open HUD Studio: default Battery °C Off. Enable it and independently
   toggle Battery W, opacity, metrics, size/layout, Adaptive Colors.
4. Verify BAT T is displayed exactly once and visually normal at typical
   Odin temperatures. Confirm compact locked HUD, independent 48dp unlock
   touch target, corner parking, explicit collapse, REC/STOP and QS still work.
5. Compare BAT T with device's Android battery information (subject to
   reporting delay) while unplugged and charging.
6. Synthetic unit tests cover 39/42°C transitions, 15s qualification at
   2s and 5s sampling, 10s releases, spikes, missing values, gaps and reset.
   Actual battery warmth should NOT be artificially induced for testing.
7. Check `RECORDER_TIMING` per-session P95 and verify new BAT T formatting
   does not introduce meaningful sampling/HUD overhead.
8. Instrumentation test compiles in CI but must be RUN on Android to
   confirm SharedPreferences, intent extras and setting persistence.
9. No additional API permissions, background sampler, vibration, alarm
   or vendor/sysfs critical-temperature threshold code is introduced.

## Post-test acceptance
- Safe if NORMAL remains fully unboxed; WARM and HOT outlines are confined
  to BAT T with no layout jump and no duplicate HUD.
- If BAT T is unavailable on the Odin ROM, the UI reports unavailable.
- Do not advertise battery-health overheat in reports until it is persistently
  recorded with source/provenance in a separately tested schema revision.
