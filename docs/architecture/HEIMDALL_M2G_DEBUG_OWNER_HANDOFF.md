# HEIMDALL M2-G — Opt-in debug-only single-service recording ownership

**18 November 2026 — engineering spike; not a production rollout.**

**Base:** `feature/heimdall-m2f-native-sampling-engine` at `7b7fce267827fa0606273bd32b6f14d1aa0e1187`.

## What M2-G adds

- `HeimdallDebugOwnershipPolicy`: deterministic admission/command routing that cannot admit a release build, a normal app/Quick Settings START, a nonforegroundable start, an existing legacy sampler, standby HUD, saving operation, attached overlay, unresolved raw recovery lease, unfinished SQLite recording row or an occupied debug slot.
- `HeimdallDebugEngineRuntime`: a monitor-service-owned `HeimdallSessionHost` + `LegacyGuardedEffectPort` + `EngineController`, executing the **new HeimdallSamplingLoop**, not `RecordingService`'s old loop.
- `RecordingService`: a private **debug-only** `HEIMDALL_DEBUG_START` action and `HEIMDALL_DEBUG_STOP` action. The existing foreground promotion happens first. A reservation on the **same service instance** blocks legacy commands; notification/Quick Settings STOP and DISARM are redirected to the owning Heimdall controller, never to the old sampler.
- `RecorderLease.hasRawActiveLease`: read-only raw active flag for admission. An expired lease can still indicate an unresolved owner and **must** block the new engine.
- `SessionStore.hasUnfinishedRecording`: read-only SQL existence check before `begin`, preventing new debug sessions from silently marking an existing RECORDING row interrupted.
- `HeimdallSessionHost`: if a new session row was opened but its recovery lease cannot be persisted, close the unleased row as interrupted and do not start sampling.
- New policy tests and an additional assertion in the native-engine failure-path test.

## Activation scope

The debug actions are internal service intents. They are **not** wired to any public button, tile, manifest filter, broadcast or new process. Android manifest/service declaration, application ID, signing, SQLite v9, existing telemetry, profile metadata, analytics and CSV/ZIP/DUKO formats remain unchanged.

The experiment is **not** a release-feature toggle. `BuildConfig.DEBUG` must be true and the service must receive an explicit app-internal debug-start command while background-start and notification restrictions are satisfied. No Android service gets added or exported.

Normal UI and both existing Quick Settings tiles continue to use legacy recording when Heimdall is not reserved. With Heimdall reserved, legacy START/ARM/HUD/RECOVER/null intents cannot reach the legacy dispatcher. STOP and DISARM are accepted and forwarded to the new owner. Duplicate debug START is idempotent.

Heimdall currently has **no attached new HUD** in this path; it records original SQLite samples using the existing DirectSampler/ForegroundAttribution adapter and the new sampling loop. Debug status is reflected in MonitorState without inventing live sensor readings.

## Android service shutdown and restart

The service keeps the normal foreground-service deadline: foreground notification before reading settings, recovery lease or SQLite. The debug runtime is a child of the service scope. onDestroy cancels it **without fabricating a user STOP**, and delays database close until the parent scope's children have completed. User STOP is processed by EngineController, which persists the terminal intent before joining its sampler and finalizing the original session ID.

**Important limitation:** Android's automatic null-intent restart still uses the legacy `RECOVER` flow to resume the same historical session ID; this M2-G stage does not claim end-to-end *native* process-death recovery yet. The debug path is intended for controlled, supervised testing only.

## Known limitations / explicit next gates

1. This is not enabled by default and should **not** be used to replace the existing Odin installation for normal play until a debug APK passes device tests and data is backed up.
2. App and Quick Settings currently share legacy status fields. Debug recording states are exposed, but debug sample counts and a new native HUD are not wired.
3. Existing debug evidence is observational, not a proof of every WindowManager/battery/USB/Android OEM behavior.
4. If STOP lease persistence fails, the reducer must not claim a durable terminal state, even if sampling has stopped; explicit retry and original-row recovery must be tested on hardware.
5. Native-on-restart recovery, FGS restrictions, old lease-to-native handover and robust no-orphan guarantees under forced process death need further testing before production activation.
6. Fault-injection on emulator and Odin must cover: duplicate STOP; STOP during START; existing legacy lease; open-row/lease-commit failure; sample database failure; process death while recording; terminal STOP after restart; overlay cleanup; and database archive compatibility.

## CI acceptance for this PR

- Official `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`, `:app:assembleOptimizedTest` and the existing API 33 emulator test matrix must pass.
- These runs only prove Android build/unit/instrumentation compatibility. **They do not prove actual Odin 2 Portal lifecycle behavior.**
- Keep PR draft until CI and code review are complete. Do not merge this debug integration into the release track without hardware evidence.

## Next: M2-H

Add an opt-in debug instrumentation harness exercising actual service START/STOP from within the app UID with bounded test sessions and a snapshot of original samples; use fault-injected IO to prove the single-owner handoff, durable STOP and native recovery before migrating the live UI and HUD.
