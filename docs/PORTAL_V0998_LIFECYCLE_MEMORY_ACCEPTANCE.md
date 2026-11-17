# CoreMetrics v0.9.9.8 — lifecycle, startup and RAM hardening

Draft alpha — versionCode 51, app v0.9.9.8, report format v0.9.9
and SQLite schema v9 unchanged. Base: v0.9.9.7 stability/memory draft.
No root, no network, no new permissions, no hardware controls,
no HUD theme changes, no unsolicited report preparation.

## Implemented from Claude's v0.9.9.4 review, verified against current code

### Service foreground deadline (S2, first safe step)
- The :monitor service's onCreate no longer calls HudOptions.from:
  that had opened the SQLite-backed settings snapshot synchronously.
- START, ARM, RECOVER and a null Android service restart first present a
  neutral "preparing" foreground notification, *before* journal append,
  recovery-lease checks, and SQLite settings reads. "Preparing" never claims
  a recording was actually saved. Non-starting HUD/STOP commands are excluded.
- The existing recovery validation, same SQLite session ID, terminal STOP
  reconciliation, eventual active REC/standby notification and overlay owner
  remain intact. Denied notification permission / FGS failure fails closed.
- This intentionally does not rewrite the ~500-line onStartCommand state
  machine or assume OEMs will honor START_STICKY.

### Background cache reclamation (M1/M2)
- Activity.onStop and Android trim callbacks can clear detailPoints,
  firstPoints, secondPoints, detailed analysis report and their loaded-ID
  keys. Selected session IDs remain intact; no SQLite rows are deleted.
- A current DUKO export, pending FIFO turn or known legacy SAF document
  request prevents eviction. A dedicated policy has JVM tests.
- Session and comparison loading LaunchedEffects are gated on visible
  Activity state and re-enter on foreground return; they do not immediately
  reload the arrays while Eden has the foreground.
- RAM savings are *not* claimed until the optimizedTest APK is measured.

### UI database failures (S3, incremental)
- The session list, linked sessions, Sessions points, Compare points and
  selected-session detail reads use guardedUiDatabaseRead on IO.
- Cancellation propagates normally; exceptions are journaled as
  UI_DB_READ_FAILED, surfaced in the UI, and do not overwrite an existing
  valid list with a fabricated empty result.
- Selected-session post-processing also returns a visible error instead
  of crashing composition on ordinary analysis exceptions.
- Other legacy export and settings operations still need an audited
  full-coverage pass; no global catch-all or silent database fallback.

### Process-local SQLite connection reuse (S4/M4)
- One ProcessSessionStoreOwner uses applicationContext and is instantiated
  independently in the UI and :monitor Android processes. The old UI owner
  delegates to it. Recorder, HUD configuration and Quick Tiles can share the
  :monitor helper without repeated open/close or connection-pool churn.
- SessionTile START and HudTile ARM no longer read SQLite synchronously on
  their main-thread click handler. The service reads the authoritative
  recording defaults only after meeting the foreground deadline.
- SQLite remains the source of truth for HUD and recording defaults.
  Explicit Intent extras continue to take priority. No write-behind HUD
  cache, no persistent Activity reference, no mandatory new data migration.
- HUD chip option changes still commit synchronously to SQLite for current
  durability; moving those writes to a serial IO writer requires additional
  ordering/teardown tests.

## Required hardware and CI acceptance

1. All CI jobs: JVM suite, debug and optimizedTest APK builds, API 33
   database-integrity and reports/UI instrumentation green.
2. On Odin: start REC from each QS tile and the app 20 times, including
   launching Eden concurrently. No foreground-service deadline failure,
   no fake second session, no session-ID replacement on process recovery.
3. Android: switch from a fully populated Sessions or Compare tab into
   Eden. Observe UI PSS before/after on optimizedTest. Reopen CoreMetrics:
   selection persists and real saved points/analysis load again.
4. Launch DUKO FIFO export and an older SAF report picker; rotate or
   background the app. The source arrays remain available when required,
   document picker finalization and partial-file cleanup remain valid.
5. With a deliberately corrupted/missing test database (debug only),
   verify no UI process crash from selected-session query failure, that a
   visible warning appears, and that cancellation still propagates.
6. Compare Quick Tile click latency and monitor PSS before/after
   ProcessSessionStoreOwner; verify no stale cross-process default values
   after changing interval and HUD settings from the UI.
7. Check START/ARM/RECOVER denial paths and Android 13+ permissions:
   a failed permission check must not leave a misleading FGS notification.
8. DUKO queue continues to accept reports only by explicit user action,
   exactly one active report in FIFO order; no report work while idle.

## Deferred, not silently 'fixed'

- Removing @Synchronized from long report scans until tested against
  writer/restore/delete races and SQLite snapshot-consistency requirements.
- Dedicated maintenance flag or batched imports/restore during REC.
- UI-wide database error audit for remaining legacy callbacks.
- Asynchronous, ordered HUD chip settings persistence with explicit
  success/failure reporting.
- Rewriting service into a reducer; throttling game attribution;
  global uncaught-exception handler (it must never mask or replace
  Android's original crash reporting).
- Claims of PSS, GC or frame-time gains prior to real Odin measurements.
