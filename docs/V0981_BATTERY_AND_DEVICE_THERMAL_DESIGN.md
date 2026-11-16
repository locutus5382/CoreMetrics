# CoreMetrics v0.9.8.1 — optional battery-temperature HUD and device-aware thermal warnings

Status: **design proposal, not implementation**. Ship only after v0.9.8 JVM CI is
green, SQLite v6→v7 instrumentation is run, and the combined Odin acceptance
test establishes actual thermal-source availability.

## Why this is useful
- Compare battery heating between driver, fan and underclock profiles without
  confusing `BAT T 32.1 °C` with existing `BAT 4.2 W` (net terminal discharge).
- Recognize *OS-reported thermal pressure* while playing; current Android
  `NONE` on Odin does **not** establish absence of CPU/GPU throttling.
- Flag trustworthy battery overheat reports, and show *advisories* for
  sustained warm battery temperatures even when Android's thermal HAL is
  unhelpful; do not describe these advisories as vendor critical limits.
- Preserve sensor provenance and critical incident timestamps for later
  DUKO report analysis, without new continuous polling.

## 1. Existing foundations (as of v0.9.8)
- `DirectSampler.battery()` reads `BatteryManager.EXTRA_TEMPERATURE` from
  the same sticky `ACTION_BATTERY_CHANGED` broadcast that supplies battery %
  and power input. It converts Android's tenths of a degree Celsius to
  nullable `Snapshot.batteryTempC` and stores it in existing SQLite samples.
- `RecorderHud.update()` consumes the **existing** Snapshot every 2/5s.
  No independent update ticker, additional battery receiver, service or
  privilege is needed for merely displaying `BAT T`.
- Android device-wide `PowerManager.currentThermalStatus` is sampled already.
  Headroom is optional, throttled and often NaN on the Odin 2 Portal Android
  13 ROM. Current HUD has a 4dp warning accent on actual OS warning.
- Numeric CPU/GPU °C thresholds 65/80 in `HudSeverity` are cosmetic display
  bands, NOT universal limits or device-specific critical temperatures.

## 2. Proposed implementation, ordered

### 2A. Optional battery-temperature HUD — low risk, good value
- Add `HudOptions.showBatteryTemp:Boolean=false`, preference
  `hud_batteryTemp`, Intent extras, `selectedCount()`, settings toggle
  and illustrative preview.
- Add `BAT T 31.4°C` to `RecorderHud.renderTelemetry()` only when selected;
  retain existing `BAT 4.2W` watt label as a separate, independently toggled
  option. Missing/invalid data prints em dash, not 0°C.
- Preserve compact pill/corner/dock/lock/window ownership and all other
  options. Do not enlarge the collapsed tab by default. Keep animation static.
- Validate the existing source on Odin against the Android battery screen;
  spot-check charging and unplugged states, sensor noise/staleness and
  impossible values. No permissions or SQLite schema upgrade required.

### 2B. Reliable warning evidence and provenance
- Priority A: **Device-reported conditions**: Android thermal severity
  LIGHT/MODERATE/SEVERE/CRITICAL/EMERGENCY or a trustworthy BatteryManager
  `EXTRA_HEALTH == BATTERY_HEALTH_OVERHEAT`. Distinguish OS-wide thermal
  severity from battery health. Platform headroom forecast remains optional.
- Priority B: **Mapped vendor/kernel trip point**: only when accessible
  read-only to the app UID, sensor zone type is identifiable, trip-point
  `type` and `temp` pair are both valid, and the observed temperature
  comes from the SAME zone. A sysfs `critical` trip usually means emergency
  threshold, not optimal sustained operating temperature. Do not equate
  CPU sensor max to an unrelated zone's trip point.
- Priority C: **Display advisories**: optional, explicitly labeled
  user-configurable battery temperature bands, e.g. 40°C warm, 45°C hot.
  These are NOT device-authenticated critical or safety limits. Require
  sustained valid readings and hysteresis; no noisy per-sample flashes.
  No automatic generic CPU/GPU 'critical' thresholds.
- If none of these sources are verified, show only numeric temperature and
  'device critical limits unavailable' in Settings. Never synthesize them.

### 2C. Architecture
- Add a pure `ThermalWarningPolicy` with explicit source, severity,
  confidence, timestamp, applicability (OS/BATTERY/CPU/GPU), hysteresis and
  debouncing. OS CRITICAL/EMERGENCY and BATTERY_HEALTH_OVERHEAT bypass the
  waiting period; user advisories use >=2 valid samples and a minimum
  confirmation interval, with a lower release threshold. Null breaks advisory
  continuity, never becomes a safe or normal reading.
- Keep read-only capability validation in `DeviceCapabilityProbe`, executed
  on user request, with bounded sysfs reads. Cache supported mappings by
  device/ROM identity and do not iterate 128 zones every gameplay tick.
- Android API 35+ can expose `getThermalHeadroomThresholds()`; **do not**
  invoke this directly on API 33 Odin. On older systems, rely on supported
  `getCurrentThermalStatus()` and optional `getThermalHeadroom()`.
- If battery health should be reproducible in reports, evaluate extending
  Snapshot and SQLite (v8 additive migration) with nullable
  `battery_health` and source, but only after the reading is validated on
  device. It is not required for the optional BAT T value itself.
- Do not poll additional power, sysfs or HAL sources from `RecorderHud`:
  the HUD is a consumer of `DirectSampler` only.
- Expose an optional, static warning accent and a short detail string:
  'Android thermal MODERATE', 'Battery overheat reported by Android',
  'Battery warm (app advisory)', or 'Critical threshold unavailable'.
  Avoid alarms, blinking borders and false 'CPU throttled' declarations.

## 3. User-facing benefit and tradeoffs
- Battery °C lets a user compare heat accumulation under two drivers and fan
  modes, including when CPU temperatures are similar but battery heat differs.
  This is useful context, not a benchmark frame-rate proxy.
- OS thermal warnings help recognize device-wide pressure in real time;
  mapped validated thresholds add more context if accessible.
- Advisory display can reduce surprises during long sessions, but warnings
  must not be called 'critical' without device-supplied evidence. Failure to
  do this would erode trust in CoreMetrics comparisons.
- Additional label can widen HUD: default OFF and user-selectable, preview
  both text density and touch-lock/corner positions.

## 4. Acceptance
- JVM policy cases: absent temp, stale readings, -20/150°C input, one-sample
  spikes, sustained elevated readings, 2°C hysteresis, OS status changes,
  critical OS alert, unknown/unmapped trip points, false cross-zone mapping,
  no verified capability, API<35.
- Android: initial settings default OFF, round-trip through SharedPreferences,
  Intent options and process-recovery HUD; compare source on Odin.
- Recorder: unchanged 2s/5s cadence, SQLite sample durability, STOP/LMK,
  no extra calls while READY. P95 collector/DB/HUD timings comparable to
  v0.9.8 baseline. Overlays remain touch-through when locked.
- If a new battery-health column is adopted, test additive migration, CSV
  import/export and historic sessions.
- Manual warning text and exported reports explain that Android status NONE
  and missing headroom are *not proofs* of safe component limits or no
  throttling. Thermal throttling remains an inference unless vendor source
  explicitly reports mitigation.

Decision gate: **Implement 2A first in v0.9.8.1**, then 2B/2C only for
verified device sources; never block BAT T display on inaccessible critical
temperature trip points.
