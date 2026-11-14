# CoreMetrics v0.9.6.1 — targeted polish and hardening

**One canonical app** on PR #27. Debug version code 27. No separate Frosted/Themes installation or paid feature. Existing v0.9.6 recording recovery and telemetry sampling semantics remain.

## What changed
- Live tab first-launch, dismissible permission rationale: notifications (CoreMetrics currently requires Android 13+ grant for REC), optional overlay and optional Usage Access. No OS permission dialogs appear automatically. Returning from Android Settings updates status promptly. Reopen via Settings.
- QS HUD / REC tiles retain stable separate labels and two-line state descriptions. No ungrounded CPU temperature is rendered in Quick Settings. Thin outlined pulse OFF vs lit filled pulse ON with Android SystemUI-controlled accents. On missing permissions, the tile uses the OS-supported activity launch API to open the guide (PendingIntent for API 34+, Intent for API 24-33).
- A/B Compare **Swap A ⇄ B** exchanges selected IDs and their cached points to flip B−A without altering source data.
- Confirmed deletion clears inactive selection objects immediately. SessionStore refuses deleting an active SQLite RECORDING row or a fresh pending recording lease. Never delete a session with an unfinished user-requested recovery.
- Database schema **v5** introduces `samples_by_session_power(session_id,battery_status)` on new databases and upgrades from v1–v4. This enables covering scans for the session-list battery-status predicates, but performance depends on SQLite's optimizer and data distribution.
- Imported CSV metadata is bounded and stripped of control characters, preserving multilingual characters. StatusProvider checks caller UID in addition to exported=false. The recorder journal rotates at 48 KB rather than rewriting a 64 KB file synchronously.
- Deliberately **not** implemented: a shared mutable sysfs read buffer, speculative SQLite PRAGMA settings, continuous CPU temperature tile updates or background permission auto-prompts. These offer uncertain gains or introduce correctness/reliability costs.

## Portal acceptance checklist
- [ ] Install canonical package only after exporting recordings from any separately installed `.v093base`, `.v093themes` or Preview packages; private databases do not merge automatically. Check signing compatibility.
- [ ] First launch shows purpose, status, manual grant buttons; notifications granted, overlay optional, usage optional. Dismiss/reopen and Android Settings return behave correctly.
- [ ] From a fresh install, tap HUD and REC QS before granting permission: opens CoreMetrics setup, starts **no** unintended session.
- [ ] Tiles display inactive/active icons with accessible descriptions; long press still launches settings. A/B Swap reverses B−A delta without duplicate fetching or losing chosen source data.
- [ ] While recording and during LMK recovery, deleting the active lease ID is refused; deleting completed sessions removes A/B links and samples atomically.
- [ ] Upgrade a disposable v4 database to v5, confirm index and retained samples. `SessionStoreOperationsTest` checks the migration and basic deletion, but CI compiles this instrumentation test only; run it on a device/emulator.
- [ ] Confirm pre-existing imported CSV and manually entered Unicode game labels, driver details and telemetry text remain readable; malformed control characters cannot spoof UI/newlines.
- [ ] Run Hades ≥30 min; verify single HUD after QS restarts; compare PSS/GC and sample gaps to v0.9.6.
- [ ] Verify offline behavior, Ko-fi only in Settings/GitHub, no extra network permission and no donor-only features.
- [ ] GitHub CI testDebugUnitTest, assembleDebug, assembleDebugAndroidTest and R8 assembleRelease green. Release not production signed; keep PR draft until Portal acceptance.

## Important caveats
WAL permits concurrent readers/writers but does not make SQLite locks impossible. The new covering index increases storage and has a small insertion cost. A persisted lease has a bounded freshness window; the database `RECORDING` guard remains the second safety line. Android SystemUI decides tile layout, accent and subtitle visibility.
