# HEIMDALL M2-M — Verify original session finalized after STOP

**Development continuation from M2-M sample-growth verification (PR #65).**

## Added to isolated Odin Trial

The non-exported `HEIMDALL Trial` console includes a **Verify saved session after STOP** action, complementing the separate 8-second live sample-growth test. It is read-only: it fetches the private `:monitor` status and checks the saved **original SQLite row** and its sample count. The action never repairs, clears, rewrites or creates a session.

It reports `VERIFIED_COMPLETION` only when every independent condition passes:

- A verified monitor response and original session ID greater than zero
- The original SQLite row exists and has status `COMPLETED`
- At least one telemetry sample remains persisted under that same ID
- The raw recovery-lease owner is absent, and neither the unresolved-lease flag nor repair-required state is present
- There is no unfinished recording elsewhere in the trial database
- The monitor does not claim recording, starting or saving; no native pending/retry phase remains

Any missing condition produces a specific failure code: `MONITOR_UNAVAILABLE`, `ORIGINAL_ID_MISSING`, `ORIGINAL_ROW_MISSING`, `ORIGINAL_NOT_COMPLETED`, `SAMPLES_MISSING`, `RECOVERY_LEASE_UNRESOLVED`, `UNFINISHED_RECORDING_REMAINS`, or `MONITOR_STILL_ACTIVE`. The result, original ID and saved sample count appear in the **Copy diagnostic summary** text. This result is test evidence, not a guarantee of thermal/battery sampling accuracy, and never grants recording authority.

## Odin test workflow

1. Install the separately signed **HEIMDALL Trial** APK only after exact-head CI is successful. Check app ID `com.locutus5382.coremetrics.heimdalltrial`; do not uninstall or overwrite CoreMetrics, and keep backups.
2. Start a short trial session and note the original session ID and raw lease owner. Verify stored sample growth over eight seconds.
3. STOP using the trial console. Refresh status and tap **Verify saved session after STOP**. `VERIFIED_COMPLETION` proves the original row is completed, samples remain, and no raw recovery owner survives.
4. Copy the diagnostic report, including the growth and completion results. Any failure code means do **not** begin another trial recording until the cause is understood. Keep the original lease and database evidence.
5. Repeat with Eden/game background transitions, then cross-check DUKO/report/export compatibility. The original CoreMetrics app is never part of these experiments.

## Validation

12 deterministic JVM contract tests cover success and negative conditions, including a monitor repair-required state. Existing native runtime, native restart, terminal STOP recovery, database-integrity and reports-ui emulator groups remain required. Full Android build and isolated-trial APK package-ID checks must pass on the final commit.

This PR does not activate Heimdall as the default, change the database schema, introduce an additional Android Service or delete the legacy engine. M2-N/M2-O remain gated on real Odin evidence, user approval, preserved exports and verified rollback.
