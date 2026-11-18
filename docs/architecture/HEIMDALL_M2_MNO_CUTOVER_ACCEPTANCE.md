# HEIMDALL — Combined M2-M / M2-N / M2-O Acceptance Programme

**18 November 2026 | Preparation branch, NOT a release cutover**

Base: `feature/heimdall-m2l-truthful-recorder-status` at `a442ab9266899d6477f9c32bf1741f624327b6ac`.

## What is implemented in this branch

`HeimdallCutoverGate` is a **pure Kotlin preparation policy** and 14 JVM checks. The production service does **not** call it. It has no settings toggle, no live app/QS routing change and no deletion of legacy code. It refuses a Heimdall-default request until independently verified real Odin results, process-death and STOP results, session/export regressions and tested rollback are all supplied. Any unresolved recovery lease, legacy recording task, unfinished SQLite row, existing overlay or different live owner blocks a handoff even when other evidence is green. Its default constructed evidence cannot authorize Heimdall.

## M2-M — Physical Odin 2 Portal acceptance

Preconditions:
- Do **not** install a debug APK from a failed CI run. Choose the exact successful CI SHA and inspect the build artifact/signature before installation.
- Existing application ID is `com.locutus5382.coremetrics`; a GitHub Actions debug APK may be signed using a key different from the existing installed application. If Android rejects an upgrade with INSTALL_FAILED_UPDATE_INCOMPATIBLE, **do not uninstall** the existing app simply to continue. Back up database, sessions, exports and settings first; use a test install / safe upgrade workflow.
- There is not yet a normal on-device button for native engine selection. The existing internal debug-only `HEIMDALL_DEBUG_START` service action is **not publicly exported**, so an unprivileged external ADB shell cannot be assumed able to start it. A supported user-facing debug launcher or same-UID test harness must be implemented and validated before asking for a live native Odin test.
- Save baseline DUKO comparisons and CSV/TXT exports from the existing recorder. Note original session IDs, latest complete rows and approximate size. Never clear data in the recovery tests.

Physical acceptance matrix (record result per case, screenshot/log and original session ID):
1. Legacy baseline: no monitoring active, quick tile idle; record an ordinary game run; STOP once; confirm only one session, samples and exports.
2. Native opt-in **only after a verified in-app debug entry exists**: start one session, confirm foreground notification, one session ID, samples on disk and native owner tag; STOP and verify completed terminal row and cleared lease.
3. Double START / double STOP from the notification and tile. No additional recorder, HUD or SQLite ID. Repeated terminal cleanup must retain ended_at.
4. During a 5–10-minute Eden gameplay session, foreground/background app transitions, display off/on and game close/reopen; check sample cadence, battery and thermal readings, CPU cluster clocks, no session duplication or crashes.
5. Simulated monitor-process restart **only when the in-app debug diagnostic control supports it safely**. Verify original ID resumes, count increases and no second row appears. A previously persisted STOP must **finalize without resampling**.
6. Fault conditions: test reversible lease-write failure, sampler shutdown and SQLite-finalize error in a disposable emulator first. **Do not** deliberately corrupt production Odin files or simulate disk-full on the user's main device.
7. Quick Settings during STOP failure must show Retry STOP / Retry Save, not REC; unrecoverable raw lease must show Repair required, never a new START. The old HUD cannot be attached while native recording owns the service.
8. Compatibility: reopen existing sessions, DUKO A/B, integrity report and export selected range; compare original output files byte-wise or semantically as appropriate. No lost samples.

Exit criteria: all critical tests pass, no duplicate sampler/windows, original session IDs preserved, repeatable native session finalization, rollback rehearsed, and no outstanding severe crash/recovery issues. Capture real logcat/journal diagnostics and check thermal/power/performance overhead on Odin rather than assuming emulator numbers generalize.

## M2-N — Reversible default-engine cutover (NOT active yet)

The planned integration will introduce a *single authoritative service-side command router* with explicit modes:
- LEGACY (current default): legacy start/stop/HUD path retained.
- HEIMDALL_SHADOW: read-only observation; legacy is sole recorder.
- HEIMDALL_DEFAULT: live native engine is sole recorder **only after all gate evidence is validated**.

Do not allow a UI preference alone to authorize HEIMDALL_DEFAULT. The active foreground service must check live task, raw lease owner/session ID, DB rows, WindowManager ownership and recovery eligibility **again**, atomically with reservation; reject conflicting evidence. Existing LEGACY-owned leases must recover through their original legacy path. Foreground-service restrictions and notification permission must still be enforced.

Rollback must work without deleting session history or violating the original lease owner. A rollback while a native session is active must defer until a verified terminal state; it may not start a second legacy sampler. Any START during unresolved recovery must be refused with a clear status.

## M2-O — Remove obsolete CoreMetrics engine only after M2-N proof

Remove **only** recorder-specific legacy lifecycle code after successful default-engine cutover and rollback rehearsal:
- obsolete legacy sample-loop/scheduler in `monitor/RecordingService.kt` (keep the foreground service shell and Android lifecycle);
- duplicate START/STOP/ARM/HUD-command routing once the Heimdall engine owns all required functionality, and redundant mutable recording status flags;
- obsolete legacy HUD window operations and redundant Quick Settings path **only after** the redesigned Heimdall HUD and its one-tile workflow pass real-device validation;
- shadow adapters/diagnostic scaffolding that are no longer useful in production.

**Do not remove** `telemetry/DirectSampler.kt`, device-capability probing, CPU/GPU/thermal/battery readers, `data/SessionStore.kt` and its migration history, SQLite v9/session history, DUKO comparison and export modules, process-owner safeguards, or recovery/lease compatibility until replaced by proven equivalents. A legacy lease migration/recovery path may need to remain as a small compatibility adapter even after the old sampler is removed.

## Git and CI integration

The Heimdall work currently uses **stacked draft PRs** in the CoreMetrics repository. A branch passing CI does not automatically update `main` or produce an APK safe to overwrite a prior installation. Before any released cutover: consolidate the PR stack with a documented merge strategy, check source diff and permissions, run full Gradle builds, Android instrumentation (including process death, STOP/restart and report exports), then test an exact signed build on Odin.

Go/no-go should be tied to verifiable evidence, not to calendar dates or the number of PRs merged. Until then: legacy engine is the production fallback; new Heimdall engine is debug-only.
