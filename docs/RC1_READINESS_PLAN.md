# CoreMetrics — proposed Release Candidate 1 acceptance plan

**Goal:** a feature-frozen, reproducibly built and privately distributed **v1.0.0-rc1** after v0.9.3 single-HUD/Quick Settings recovery validation, v0.9.1 integrity checks and v0.9.2 performance/device hardening. A draft GitHub PR or successful debug build is NOT an RC. This document neither creates a tag nor claims Play Console approval.

## P0 release gates — all must pass
1. **Recorder continuity and user control:** Repeat 30–60-minute Odin 2 Portal Eden sessions, including locked HUD, background/foreground, Quick Settings, notifications, stop, hide/show, high RAM pressure and recovery after Android LOW_MEMORY. Test STOP is final, no orphan HUD, preserved SQLite rows, honest interruption/gap accounting. Android's START_STICKY is best effort, not immunity.
2. **Correct numerical evidence:** Validate medians/P95, original time bounds, gap coverage, actual discharge W, CPU/GPU loads, cap observations and B−A with independent recomputation from REAL source CSVs. Full/segment/A-B TXT/CSV/PNG must agree. Test 2s and 5s, interrupted and short sessions, charging, missing sensors, storage errors. No invented FPS or throttle claims.
3. **Functional/device usability:** Live → Record → Analyze → Compare → Export/Share must work via touch and D-pad. Correct permissions, HUD touch lock/unlock and predictable recovery. Zero unresolved data loss, crash/ANR or blocking UI bugs. Approve one or more of the four finishes, remove rejected ones rather than endlessly polishing.
4. **Stable release package and update path:** Decide canonical com.locutus5382.coremetrics application ID, production icon/name, versionCode progression, expected migration from earlier test packages (CSV import fallback). Test release build install/upgrade. Current v093base/v093themes side-by-side APKs have different IDs and debug test signatures: they are NOT the production artifact.
5. **Signed build:** Maintain secure upload key outside GitHub source, configure Play App Signing for Play, generate a release-signed Android App Bundle, record SHA/checksum and tag/commit provenance. Current workflow builds debug APKs only; it has not demonstrated a signed release AAB.
6. **Policy/security/privacy:** Prepare truthful privacy policy (public URL and in-app link), Data safety form, permissions disclosure, metadata/screenshot/store listing, content rating, audience and support contact; review third-party library licenses and user's All Rights Reserved license. No INTERNET permission at present. Test optional overlay and Usage Access denial. Google Play must approve the specialUse foreground-service declaration; manifest alone does not guarantee approval.
7. **Android compatibility:** Test at least the Odin 2 Portal and one other supported Android device/API level. Handle unsupported sensors with 'Unavailable'; verify notifications, battery/overlay restrictions, landscape and accessibility basics.

## Recommended but non-blocking for *private* RC1
- Instrumented UI tests covering SAF PNG + Android share chooser, Android service lifecycle, permission refusal and device configuration changes.
- Quantified RAM and battery impact during idle/standby, recording, analysis and repeated PNG exports; run CPU profiler and watch for UI jank.
- Small trusted tester group, release notes, reproducible problem-report procedure and explicit retention of user-owned session exports.

## Scope freeze
- Defer deterministic three-finding summaries to a later opt-in update; they are not required for a trustworthy monitoring/diagnostic RC.
- Do not add event markers, thermal soak detector, new hardware controls, FPS workarounds, cloud accounts or additional styles before RC1.

## Suggested order
1. Finish v0.9.3 single-HUD/Quick Settings recovery checks, v0.9.1 integrity and v0.9.2 performance acceptance on the Odin; repair verified P0/P1 bugs before any RC tag.
2. Choose/lock supported finishes and establish production package/release signing and AAB CI job.
3. Verify no P0/P1 outstanding and pass a release-signed install/update test.
4. Tag a single immutable release source v1.0.0-rc1; run the complete regression matrix; distribute privately.
5. Separately submit for Play Console internal/closed testing and permission review; proceed to production only after policy and test eligibility gates.

## Current evidence and unknowns
- Repository currently uses Kotlin/Compose, SQLite WAL, targetSdk 36 / minSdk 31 and a specialUse foreground recorder, optional SYSTEM_ALERT_WINDOW and Usage Access, and no INTERNET permission.
- Current v0.9.2 CI does JVM tests, debug APK builds and Android instrumentation **compilation only** (no emulator execution). Device UI/share/LMK recovery, production signing, Google Play declarations and public store materials are not verified.
- One prior Odin session had an Android LOW_MEMORY :monitor termination. v0.8.8.1 requested sticky continuation and restored lock preference but cannot guarantee survival.
- New personal Play Console accounts created after 13 November 2023 generally require a 12-person/14-day closed test before production eligibility. Confirm the account's actual eligibility; internal testing has different rules.
- Google Play review of specialUse and overlay-related UX remains an external approval risk, not an engineering certainty.

**Go/no-go:** no known crash/data-loss or misleading data; complete full workflow on release-signed build; reproducible test evidence at the exact RC1 tag. Cosmetic improvements can wait.