# CoreMetrics v0.9.7.2 — Odin 2 Portal HUD and analytics acceptance

This branch follows the actual v0.9.7.1 Odin test (Eden/Mario Kart 8).
Session #1 contained 97 saved readings over 507.9s, one 32.873s
timestamp gap, estimated 94.5% continuity, and a persisted same-ID
REC recovery after Android LOW_MEMORY monitor process exit. It also
observed policy3 max 2.80 -> 1.79 GHz and policy7 max 3.19 -> 1.84 GHz
at 312.874s, during a manual ClusterTune setting change. Neither
event is proof of a crash caused by CoreMetrics or a gameplay FPS effect.

## Scope and safety

- Keep normal standalone-app permissions: no root, PServer, internet,
  frame-capture/FPS or system-write APIs.
- No changes to :monitor recording service, Quick Settings tiles, REC/STOP,
  sampling interval (2/5s), recovery leases, SQLite v6 or raw CSV fields.
- App version v0.9.7.2 (versionCode 35); report format remains v0.9.7.
- CI debug/optimized test builds have temporary debug signatures that
  may differ across runs. Export important sessions before any uninstall.
- Compilation and instrumentation APK generation do not replace actual
  touch/overlay/device validation.

## In-game overlay acceptance

1. Launch Eden and arm HUD using the existing Quick Settings tile. Confirm
   READY standby appears without starting a sensor-sampling session.
2. Tap REC. Verify readings are legible above and controls aligned along the
   outer right edge below the readings; no large controls over the game center.
3. Select HUD Studio Small, Medium, Large, pill/card, opacity and metric options.
   Confirm the on-screen preview changes immediately (illustrative values)
   and that actual HUD dimensions/typography change accordingly.
4. Confirm `▌ OS OK` is visible while Android OS thermal status=NONE, rather
   than an almost invisible dot. OS `?` is grey when unavailable. Do not
   interpret OS NONE as proof no hardware frequency-limiting occurred.
5. Tap `⇥` or drag to the right/left/top edge. Verify the collapsed
   `CM / ● REC` tab remains conspicuous, fully onscreen, clickable, and
   has a short tap area. Expand to restore the same recording and settings.
6. Test locking while expanded and unlocked tap-through recovery. Confirm
   the independent unlock handle sits over the correct lock button; no
   stranded unclickable overlay. Repeat after rotating/returning from Eden
   if applicable; do not assume an orientation lock.
7. Open quick HUD drawer, modify metric toggles, dock and expand. Verify
   no duplicate overlay windows. Confirm HUD hide/show from Quick Settings
   does not stop REC, and a second app launch does not start another session.
8. Test deliberate Android memory-pressure recovery, if it occurs naturally
   during gameplay. Verify session id and prior sample count survive,
   and the gap is visible as a gap (not an interpolated line). Do not
   force-stop or intentionally destroy irreplaceable session data.

## Analysis and PNG acceptance

1. Open session #1 or a comparable test recording. Basic analysis should
   immediately show summary, integrity and observed hardware findings.
   The dense interval/extraction controls remain under `Adjust interval /
   export`; detailed data are under `Advanced diagnostics`.
2. Session #1 should warn that CPU frequency ceilings changed, meaning
   the full recording spans two observed cap configurations. The label
   `Stock` is manually set and does not authenticate tuner identity.
3. Export the single-session PNG: the row should be `Recording continuity`
   with PARTIAL, ~94.5%, and 1 gap; not `Telemetry integrity 80/100`.
   The badge should caution about recording quality despite the stored
   session being COMPLETED.
4. Validate on both debug and optimized test builds. Check basic/advanced
   settings persist when browsing away from a session; ensure no excessive
   memory growth or app-specific crash signatures.
5. Save logs, CSV and screenshots from any genuine failure. Android
   LOW_MEMORY is distinct from Java CRASH, and user-visible seamless
   recovery does not imply uninterrupted telemetry.

## Defer

- Independent A/B phase markers, extra recorder fields, privileged access,
  automatic game scene inference and FPS are out of scope.
- Refine thermal policy rendering only from real Portal readings; do not
  guess missing headroom.
