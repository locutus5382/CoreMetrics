# HEIMDALL M2-M — Verify completed STOP against original saved session

**18 November 2026 — isolated Odin trial only.** Based on PR #65 (`b17355ef5a76da3d482f0fc93a55dc3a37cb4d25`).

## Scope

The separate `heimdallTrial` app has an optional **Verify completed STOP and saved session** control. It verifies **persisted facts** from the original SQLite row and the monitor's private, same-UID recovery-lease status. It never creates/finishes a session, clears the lease, changes application IDs or enables the native engine as CoreMetrics' default recorder.

## Tested user workflow

1. Install an **exact successful CI `heimdallTrial` artifact**. Its package ID must be `com.locutus5382.coremetrics.heimdalltrial`; the existing CoreMetrics app and history stay separate.
2. Open the HEIMDALL Trial console and start a short native recording. Note the original ID, verified owner `HEIMDALL_DEBUG`, SQLite row state `RECORDING` and sample count.
3. Use the separate **Verify persisted sample growth (8 seconds)** control and expect `VERIFIED_GROWTH` for the same ID.
4. Tap **STOP / retry finalization** in the trial console. Before dispatching the STOP intent, it rechecks private monitor ownership and SQLite evidence, then retains an in-memory original-session/row-count baseline. A foreign or unreadable owner means STOP is not sent; the existing notification/Quick Settings may still offer service-level STOP.
5. Once saving completes, tap **Verify completed STOP and saved session**. It independently re-reads the private monitor and the original SQLite row. The check requires every property below:
   - A verified pre-STOP native-owned recording row with a positive original ID and existing session count
   - Same original SQLite session ID afterward
   - No additional SQLite session row inserted **between the baseline and completion**
   - Status `COMPLETED` and a non-null, positive persisted `ended_at`
   - Positive sample count after completion, never less than before STOP
   - Raw recovery owner cleared, no unresolved lease and no repair marker
   - No unfinished `RECORDING` row, and engine phase now idle/absent
6. Copy the diagnostic summary. A failure code (e.g. `LEASE_STILL_ACTIVE`, `ROW_NOT_COMPLETED`, `ORIGINAL_SESSION_CHANGED`, `EXTRA_SESSION_ROW` or `STATUS_UNAVAILABLE`) is useful evidence; **never remove an unfinished session or repeatedly start a new one to force a pass**.

The verification baseline is intentionally held in the console's memory after STOP. If Android kills the trial console, close/reopen and collect a fresh status report; the special before/after verification cannot be claimed without its pre-STOP baseline. Recovery after process death has separate emulator tests and will be explicitly validated on Odin.

## Implementation

- `trial/HeimdallTrialSavedSession.kt`: read-only pure evidence policy and explicit error results, no Android dependencies
- `HeimdallTrialActivity.kt`: one extra user-triggered verification action, safe pre-STOP owner recheck, persisted original-session row/end timestamp and total-session-count reads on the IO dispatcher, diagnostic summary
- `HeimdallTrialSavedSessionTest.kt`: 13 JVM test methods (including parameterized negative cases)
- `HeimdallDebugServiceAndroidTest.kt`: existing disposable API 33 recording test now also asserts real finalization evidence, original ID, preserved SQLite samples and no additional session row

## Acceptance and boundaries

The Android service remains the *only* recorder owner; no parallel sampler, database/schema change or new foreground service. Production CoreMetrics and its existing sessions remain untouched. UI tests prove only the console/policy; the actual emulator recording test checks service behavior on a disposable emulator. These tests are not a substitute for Odin 2 Portal measurements, power management or AYN device recovery.

Keep this branch draft until `assembleHeimdallTrial`, package-ID isolation, JVM tests, all emulator groups and the original-session proof test pass. M2-N default activation and M2-O deletion remain gated by **real Odin session/recovery and report compatibility tests** plus a verified rollback.
