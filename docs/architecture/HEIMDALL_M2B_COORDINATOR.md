# HEIMDALL M2-B — Serialized coordinator and effect boundary

**Status:** Isolated, not wired to any Android Service. 18 November 2026.
**Base:** `feature/heimdall-m2a-state-foundation`, PR #50. No Gradle module split, manifest or database migration.

## Relationship to Claude's proposed architecture

- **Adopted:** pure state/event/effect reducer; a separately owned `EngineController` and an `EngineEffectRunner`; small injectable platform boundary.
- **Deferred:** ten-module extraction, new Messenger IPC, dual production engine-host modes, changes to sample representation and any rewrite of `RecordingService`.
- **Preserved:** original repository `locutus5382/CoreMetrics`, Android ID `com.locutus5382.coremetrics`, SQLite v9, data formats, 2/5-second sampling, existing `:monitor` process and the production recorder.
- The new coordinator is in an Android-free `domain` package **inside the current app module**. None of the legacy Android entry points invoke it.

## Added components

1. `EngineController.kt`: one coroutine serializes low-frequency command/lifecycle events and calls the pure reducer; a separate FIFO coroutine interprets effects. A blocked session-open does not block command intake.
2. `EngineEffectRunner.kt`: eight effect operations behind a mockable `EngineEffectPort`; only a confirmed platform operation returns a success event. Exceptions map to correlated error events, and `CancellationException` is rethrown.
3. `EngineContract.kt` and `EngineReducer.kt`: durable STOP and actual sampling-pause acknowledgements; explicit resume/interrupt-closure errors.
4. `EngineControllerTest.kt` plus extended reducer tests cover cross-operation ordering and major failure modes without requiring Android.

## Key STOP correction

M2-A still allowed a platform's successful STOP persistence to schedule finalization even if pausing the sampler had failed. In M2-B:

- The effect order is **PersistStopIntent**, **PauseSampling**, matching the existing `RecorderLease` STOP-before-cancellation policy.
- Only after **both** `StopIntentPersisted(sessionId,stopId)` and `SamplingPaused(sessionId,stopId)` does the reducer emit `FinalizeSession`.
- STOP persistence failures and sampler-pause failures keep the original session ID in `StopRequested`, with explicit retry.
- A finalization failure remains `FinalizeFailed`, without reopening sampling.
- An interrupted-session closure retry correctly calls `CloseInterruptedSession`, not a regular completed-session finalizer.
- A failed resume retains the original session ID for later durable reconciliation.

This is an event-handshake contract, **not proof that Android operations are already reliable**. The eventual port must acknowledge only after real lease, sampler and database operations complete.

## Fault and crash limits

- A process killed **before** the STOP intent commits cannot recover that request from memory. Preserve the existing durable lease and database reconciliation behavior in the real platform adapter.
- The event and effect queues are `Channel.UNLIMITED` to avoid a two-way channel deadlock. They contain **only low-frequency control events, never telemetry samples**. M2-C must validate/rate-limit external commands before making this service-visible.
- Attachment confirmation does not prove Android actually displays the overlay unobscured; detachment must be acknowledged by the owning `WindowManager` host.
- Canceling the isolated controller is not an explicit user STOP; on process death the reconciler is authoritative.

## Verification

- Kotlin 1.9.0 compiles the four production Kotlin files on the JVM using the already included coroutines dependency.
- **37 local checks passed**: 27 deterministic reducer and 10 fake-port coordinator tests using a local lightweight JUnit-compatible harness.
- GitHub Actions JVM, API 33 instrumentation, release-like build and Odin hardware validation still apply. Local checks are not represented as Android release qualification.

## M2-C integration gates

1. Keep the current `RecordingService` working while implementing an opt-in debug adapter around `RecorderLease`, database and sampler. **No second sampler or independent session writer.**
2. Verify a cold-start read-only state projection through the existing `StatusProvider` before selecting new IPC.
3. Add Android instrumentation for durable STOP, permission/FGS denial, stale Quick Settings commands, service destruction, restart recovery and duplicate-window avoidance.
4. Measure actual device RAM and recorder reliability on the Odin before enabling any new coordinator behavior.
5. Do not touch SQLite schema, session data or legacy UI/HUD in this PR.

**Merge policy:** stacked draft PR until M2-A and M2-B GitHub CI pass; no claim of Odin validation.
