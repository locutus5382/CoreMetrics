# HEIMDALL M2-L — Truthful recording status and recoverable STOP failures

**18 November 2026 · builds on M2-K, commit `8024d2b42b4f1ded56ec3a72f3eb80e1ee49d0c1`.**

## Why this milestone matters

The native Heimdall reducer has detailed STOP/recovery states, but `RecordingService.observeHeimdallDebugState()` previously mapped `StopRequested`, `Finalizing` and `FinalizeFailed` to `MonitorState.running=true`. After a durable STOP, no samples are actually being collected; pretending that the recorder is still live is misleading. The Quick Settings Session tile also treated every `saving` state as WAIT, making it impossible to request a safe retry from a failed STOP or SQLite finalization.

Also, a stale on-disk recovery lease can be ineligible for `RecorderLease.recover()`; absent a separate raw-lease check, a provider-only monitor process and Quick Settings could falsely show IDLE/START, leading users to try to open a competing recording. This is dangerous even when the service later refuses that START.

## Implementation

- `HeimdallRecorderStatusPolicy`: pure, testable mapping of authoritative `EngineState` to IDLE, STARTING, RECOVERING, RECORDING, STOPPING, RETRY_STOP, FINALIZING, RETRY_SAVE and NEEDS_REPAIR. Only `RecordingState.Recording` yields `recording=true`. All STOP/finalization states yield `recording=false`. Errors are surfaced as stable, non-sensitive codes, not arbitrary I/O messages.
- `RecordingService`: projects the pure policy to `MonitorState`, including retry eligibility, original ID, issue code and meaningful status text. This remains in the existing monitor process and does not change the reducer, sampler or session store.
- `StatusProvider`: exposes the debug-only phase, original ID, retry availability, issue code and unresolved raw lease status through the already private, UID-protected Bundle. These diagnostics are presentation only; no new command method or exported capability was introduced.
- `QuickTileSnapshot`: authoritative debug phase overrides generic SAVING when STOP or finalization failed. A raw active lease that is stale/ineligible or has an unknown owner yields REPAIR_REQUIRED, never IDLE. A HEIMDALL_DEBUG lease in a non-debug release build also cannot be mistaken for a legacy-recoverable lease.
- `QuickTileStatePolicy`: adds STOP_RETRY and SAVE_RETRY, both sending the **existing STOP action to the current engine owner**; REPAIR_REQUIRED issues WAIT and forbids a new session. HUD remains unavailable for these error states.
- `SessionTile` displays Retry STOP, Retry Save and Repair required instead of misleading REC/Saving labels. `HudTile` never attaches an overlay during these states.
- Tests: 11 pure JVM projection checks, two Quick Settings mode checks, and four isolated Android instrumentation tests for expired lease, unknown owner, retry visibility and legacy lease compatibility. Android tests use test-only SharedPreferences and do not record hardware data.

## Safety / compatibility

The default CoreMetrics recorder and its shared collectors, database v9, migration logic, session archives, DUKO analytics and exported formats remain intact. Heimdall's engine still requires a debug-only service action. The UI sees diagnostics; it does not gain a new arbitrary repair operation or silent permission to clear original evidence.

A STOP retry in `StopRequested` reuses the same durable session and stop IDs. A save retry in `FinalizeFailed` only retries terminal cleanup via M2-J; it cannot resume sampling. If the lease is missing/foreign/expired and auto-recovery cannot be verified, REPAIR_REQUIRED deliberately refuses to start another recording. Full user-guided repair is not implemented by this PR.

No claim is made about having validated Android OEM behavior on the Odin 2 Portal.

## Default-engine cutover / deletion criteria

The next steps are explicit gates, **not promises tied to a calendar**:

1. **M2-L CI green:** full Android build/JVM + all 5 API33 emulator groups (including the new Quick Settings recovery tests). No default-engine flip.
2. **M2-M Odin test build:** run a disposable controlled session under actual Eden/game transitions, STOP while sampler is writing, a foreground-service interruption, notification/QS recovery and exports; keep a backup of existing valuable session records. Verify thermal/power/telemetry consistency and report evidence.
3. **M2-N default engine cutover:** a reversible feature gate redirects normal in-app and Quick Settings START to Heimdall. No dual sampling loop, dual SQLite writer or extra HUD. Keep legacy fallback only for pre-existing LEGACY-owned recovery leases; watch for regressions, especially Android 13 AYN behavior.
4. **M2-O deletion:** once hardware and full session/report regressions pass, remove the old `RecordingService` legacy sampling/lifecycle branches, obsolete legacy HUD and redundant Quick Settings tile logic. Keep the single Android foreground-service component and the useful collectors, SQLite v9, reports, and exports. Avoid a big-bang rewrite or repo migration.

Because PRs #50–#61 are stacked, a consolidated review/merge path must be decided before release; do not confuse successful feature-branch CI with `main` being up-to-date or a production release.

## Acceptance

M2-L is **not complete until GitHub CI passes** for its final head SHA. Keep the pull request draft until all jobs, including Android instrumentation and optimized build, finish. Release/distribution and Odin hardware validation remain separate gates.
