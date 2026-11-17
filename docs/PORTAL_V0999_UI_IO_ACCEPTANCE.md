# CoreMetrics v0.9.9.9 — Claude UI I/O follow-up

Draft alpha · versionCode 52 · report provenance v0.9.9 · SQLite v9 unchanged.
Based on v0.9.9.8 Claude lifecycle/RAM hardening. Do NOT merge or release
without the required CI and Odin 2 Portal tests.

## Applied, directly supported by Claude's review

1. **Initial HUD settings no longer open SQLite during Compose composition.**
   The UI starts from a non-persisted default while the actual saved HUD
   settings load on an IO coroutine. A late initial read never overwrites
   an explicit recent HUD edit. ARM refuses until the initial load finishes,
   preventing the initial default from being saved over an existing profile.
2. **HUD settings writes have one serial, cancellable UI actor.**
   Rapid chip/slider edits are coalesced, never doing `HudOptions.save()`
   synchronously on the Compose event handler. ARM enqueues a durable
   confirmation barrier and only starts the foreground service after all
   older HUD writes are ordered behind that selected value. ARM passes the
   authoritative selected HUD settings as Intent extras as before.
   Service-side overlay chip SQLite writes remain synchronous and require
   separate ordering/lifecycle validation before conversion.
3. **Successful import/restore no longer crashes on a failing subsequent
   UI list refresh.** The error is journaled and shown; committed SQLite
   rows are not lost or mislabeled as an unsuccessful import.
4. **Debug-only StrictMode diagnostics in both UI and :monitor processes.**
   `CoreMetricsApp` reports main-thread disk reads/writes and leaked
   closables/SQLite helpers to logcat. It never terminates on a violation,
   and is disabled in optimizedTest and release variants.

## No unrelated changes

- No new permissions, root, network, hardware controls, sensors, sampling
  cadence or false FPS claims.
- Five existing HUD styles, color themes, WindowManager ownership, recovery
  lease, DUKO FIFO queue and saved sessions remain unchanged.
- SQLite schema 9, DUKO format v0.9.9, import/export formats unchanged.
- No assumed RAM savings or latency numbers from an unprofiled static review.

## CI gates

- JVM regression tests pass; debug APK and optimizedTest APK compile.
- Android API 33 database-integrity and reports-ui instrumentation pass.
- No `Unresolved reference`, Compose or Kotlin smart-cast compile errors.
- Confirm the manifest references `.CoreMetricsApp` and that neither
  release nor optimizedTest enables debug StrictMode.

## Odin 2 Portal acceptance

1. Create nondefault HUD settings, force-stop and reopen. HUD options must
   be restored (not defaulted). Repeat via CM HUD tile and after app switch.
2. Rapidly toggle HUD chips/size/opacity, then immediately launch standby.
   The first overlay must use the latest options, and the later Quick Tile
   must show the same persisted options. Repeat at least 20 times.
3. Start and stop a recording in the floating HUD with Eden running.
   No duplicate HUD; one SQLite session; 2-second and 5-second intervals
   behave as in v0.9.9.8; notification and stop recovery unchanged.
4. Import CSV and restore ZIP. Verify the new sessions and associations
   appear. Simulate a list-refresh failure in a debug test fixture:
   data must remain committed and a visible message must replace an app crash.
5. On a debug build: `adb logcat -s StrictMode` during start, HUD chip taps,
   Quick Settings actions and export. Compare main-thread I/O between
   v0.9.9.8 and v0.9.9.9. Fix remaining service HUD-chip writes only after
   testing their ordering and teardown.
6. In optimizedTest, monitor UI and :monitor PSS and 60-minute recording
   integrity. No claimed quantitative gains until collected on Odin.

## Deferred after this change

- Monitor-side HUD-chip writes: currently synchronous SQLite for durability;
  requires one ordered IO writer with crash/recovery tests.
- Full UI callback audit: some legacy export, reporting and settings actions
  still need read/error isolation.
- Long report scans versus concurrent delete/restore and archive maintenance
  gating. Do not strip synchronized access without snapshot/race tests.
- Service reducer refactor, game attribution throttling and dependency upgrades.

