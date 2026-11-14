# CoreMetrics 0.9.6.5 — read-only Odin capability acceptance

**Package**: `com.locutus5382.coremetrics` · versionCode 31 · versionName `0.9.6.5-probe-alpha`.

This is NOT the v0.9.7 sensor collector; it tests ROM and SELinux access only. No changes to the recording loop, sample persistence, HUD or Quick Settings tiles.

## Before installation
- [ ] Export valuable session CSVs and diagnostic reports. CI test APKs may use an ephemeral signing certificate; uninstalling a previous build will remove its private database.
- [ ] Install either the optimized or debug variant, not both; keep `main` untouched.

## One-shot report checks
- [ ] From Settings → **View process-exit & lifecycle log**, use **Copy report** (or export) and inspect the **Odin/Android read-only capability probe** section.
- [ ] Verify the timestamp is clearly at **report export**, not the time of a process exit.
- [ ] Check Android current thermal status and `getThermalHeadroom(10)`; "unavailable (unsupported/NaN)" is a valid device result.
- [ ] Check `/proc/meminfo` is readable and **SwapTotal, SwapFree, SwapUsed** are reported. They are swap *allocation*, not zRAM compressed physical bytes.
- [ ] Check `/proc/pressure/memory` reports a readable `some` and/or `full` PSI line; note if blocked by the ROM.
- [ ] Check `/sys/devices/system/cpu/cpufreq/policy7/stats/time_in_state` reports a readable state count, min/max kHz and cumulative kernel ticks; "unavailable" is acceptable when disabled or restricted.
- [ ] Export one report with Eden not running and another after a gameplay session. Probe data are sampled at report time, not during the session; they cannot establish causal LMK diagnosis.
- [ ] Attach the exported report to our discussion, preserving the header and unavailable/error states.

## Non-regression
- [ ] REC/STOP/READY/Quick Settings behavior matches v0.9.6.4, including no uninvited post-STOP standby resurrection.
- [ ] Keep one actual game session and export it. No new FPS claims, no 15s SQLite batching, no thermal-resistance/fan-speed prediction.
- [ ] CI unit tests and debug/release/optimizedTest assembly pass; Android instrumentation tests compile but still require a device run.

**Release gate**: this is a diagnostics probe build only. Stay on draft PR #27 until Portal testing is complete.
