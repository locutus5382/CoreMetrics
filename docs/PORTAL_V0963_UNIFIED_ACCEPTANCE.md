# CoreMetrics v0.9.6.3 — Portal stabilization acceptance

**Scope:** v0.9.6.3, Android versionCode 29, same app package, same four runtime themes, no new sensor permissions, no root, no Internet. Branch `feature/coremetrics-v094-unified-qs`; draft PR #27. Do not merge to main until physical tests.

## Before installing
- [ ] Back up valuable CSVs and reports. GitHub CI may use a different Android debug signing key from the previous build, requiring uninstall; old app-private data is lost on uninstall.
- [ ] Install only ONE of the available 0.9.6.3 builds, preferably R8 optimized test. Both contain the same `com.locutus5382.coremetrics` app.
- [ ] Check four app themes still work, including Frosted Glass default.

## Drawer and geometry (video required if a defect persists)
- [ ] Enable and disable every HUD metric: ON = quiet translucent frosted-white chip/check; OFF = muted text only, no solid fill. Confirm drawer REC and opacity controls are neutral.
- [ ] With 2, 4 and 6 telemetry metrics active, check natural HUD width, no large blank strip, no clipped REC/gear/dock/lock actions and no unexpected multi-line word wrapping.
- [ ] Expand drawer (wider if necessary), close drawer (shrinks again), minimize/dock right, minimize/dock left, drag to top and dock; verify all actual dock tabs snap flush to the relevant edge, labeled CM / REC, and are easy to restore.
- [ ] Move from landscape to other supported orientation; no off-screen controls or duplicate tracked HUD windows.
- [ ] Lock/unlock retains one 48dp touchable recovery chip; no untouchable trapped overlay and no covered game controls.

## Metadata
- [ ] In Sessions → Edit, choose Custom graphics driver → type `T30` → keyboard Done or Save driver. Check **inline success** and refresh showing `Custom: T30`. Reopen editor to verify persistence. Test `Custom: StevenMXZ R28`, System and None.
- [ ] Test Custom resolution `@200`, plus invalid/blank values. A failed save must show an error without clearing the previous tag.
- [ ] Confirm active RECORDING sessions are still protected from edit/delete.

## Recorder, QS and LMK
- [ ] Arm standby, launch Hades/Eden, use Quick Settings REC. Verify exactly one session and HUD (overlay remains visible through gameplay) and 5s sampling; no extra sampling while merely armed.
- [ ] With CoreMetrics Activity open and standby/REC in progress, HUD tile should say **Paused in app** instead of misleadingly saying Off; toggles should preserve explicit user intent.
- [ ] Close Activity and check HUD appears. Toggle HUD visible/hidden during REC without stopping the recording. REC tile remains independently accurate.
- [ ] Press STOP via the QS tile and verify saving/final session status. Repeated STOP or service recreation must not create new sessions.
- [ ] If Android kills `:monitor` while recording, it may resume only the SAME valid session ID; gaps remain visible. If it dies after STOP was requested, it may FINALIZE ONLY, never resume sampling.
- [ ] Export process-exit and lifecycle diagnostics, three CSVs and reports after the test. Device logcat is needed to diagnose an actual Java exception vs LOW_MEMORY. Note UI/monitor PSS during active gameplay.

**CI limitation:** `assembleDebugAndroidTest` compiles instrumentation tests (including persistent Custom-tag readback) but does not execute them on an Android device; test on Portal before public release.
