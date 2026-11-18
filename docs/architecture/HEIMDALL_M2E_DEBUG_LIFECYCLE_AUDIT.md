# HEIMDALL M2-E — Debug-only service lifecycle evidence

18 November 2026. Base: feature/heimdall-m2d-guarded-effect-port at 119a84fc0b5886f65af8e32afb85b9403e3600e3.

## Scope

M2-D defined the guarded effect port and the ownership-transfer contract. M2-E instruments **real legacy RecordingService confirmations** but does not transfer ownership, send new commands, write SQL, sample sensors or change the existing recorder's control decisions.

- domain/LegacyServiceAudit.kt: pure event reducer with service epoch, sequence, original session ID, terminal STOP outcome, sampler exit, SQL finalization and discrepancy codes.
- monitor/HeimdallServiceShadowAudit.kt: synchronized, in-memory, debug-only observer in :monitor.
- RecordingService.kt: read-only observation at session/lease creation, STOP/DISARM lease persistence, sampler finally, database finalization and service teardown. persistTerminalIntent returns the pre-existing success Boolean for diagnostics only.
- StatusProvider.kt: optional heimdallShadow* fields for debug diagnostics; provider remains private and command-free.
- LegacyServiceAuditTest.kt: 18 JVM checks for normal and failure transitions, including recovered terminal STOP.
- HeimdallShadowPlatformAndroidTest.kt: verifies optional provider fields without starting recording.

## Evidence semantics

SessionAcquired means original SQLite row and recovery lease were written. TerminalIntent is the real RecorderLease.requestStop() result, not merely STOP requested. SamplerExited means a local coroutine reached finally after its sample loop; it is distinct from SQL finalization. Finalized records SessionStore.finish(originalId) success/failure. RecoveredTerminal represents a new service process finalizing a previously persisted STOP without claiming its own sampler exited. ServiceDestroyed is best-effort only: Android may kill a process without onDestroy().

The observer records discrepancies; it never resolves them. The debug snapshot is **not recording truth** and never authorizes effects or recovery.

## Existing functionality unchanged

RecordingService remains the only live recorder; DirectSampler, SQLite v9, :monitor, existing two tiles, current HUD, permissions, CSV/ZIP/DUKO formats and app identity are unchanged. No new dependencies or network capability. The pre-existing STOP-lease write failure fallback is not fixed in this PR; the observer flags this as non-durable.

## Verification

90 local JVM tests passed using a lightweight JUnit-compatible runner (72 prior plus 18 new). CI/Android instrumentation and real Odin 2 Portal validation remain separate gates. No release activation of the new coordinator.

## Next stage

M2-F: implement a debug-only service-owned effect adapter with explicit exclusive-ownership transfer, then fault-inject STOP commit failure, sampler join, SQLite finish, process restart and overlay-detach failure. Never allow both the legacy service path and EngineController to write the same session.