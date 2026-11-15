# CoreMetrics v0.9.7.3 — interaction polish acceptance

Development test candidate. Based on v0.9.7.2. Keep current telemetry, storage,
lifecycle, and HUD designs unchanged. Do not merge or distribute before Odin tests.

## App animations in all four surface finishes
1. Switch between Frosted, True Glass, Modern Colorful, Technical Cockpit.
2. Tap ordinary filled, outlined and text buttons and FilterChips; watch for a
   subtle **140 ms** press/release highlight and **1.5% maximum** scale-down.
3. Verify buttons remain focusable via game controller and accessible by
   normal semantics, disabled buttons still cannot trigger actions, and fast
   repeated clicks do not leave a stuck visual state.
4. Watch idle Settings and Sessions for any ongoing animation. There should
   be none unless an input is active or already releasing.
5. Confirm existing theme preview tiles and CMActionPill use the same timing,
   with no bounce or moving shine band.

## HUD expand / collapse
1. With Eden open, arm HUD and explicitly start recording.
2. Collapse to right, left and top edges; tap to expand each time.
3. Visual change is a **single 150 ms fade/scale**, not a continuously moving
   overlay, and it does not delay window geometry/touch ownership changes.
4. Repeat rapidly, drag, and test while the recorder is busy. Confirm:
   **one** HUD instance and **one** recorder; no frame-covering invisible
   touch rectangles after collapse.
5. Test touch-lock/unlock before and after docking; locked state must retain
   its independent unlock control and remain game-touch-transparent.
6. Kill :monitor via test methods and allow recovery. The restored HUD must
   not show a stuck partial-opacity/scale state or duplicate overlay.
7. Stop and disarm immediately during a transition: no remaining callbacks,
   animations or view ownership after teardown.

## Measurable efficiency
1. At 5-second and 2-second intervals, compare recorder timing and sample
   continuity with v0.9.7.2 (same game/scene settings where possible).
2. Geometry cache should avoid repetitive updateViewLayout calls when
   overlay dimensions are unchanged; cache is invalidated on new HUD attach
   and successful teardown.
3. OS thermal-badge background drawable should only be reconstructed when
   status classification changes, not on every sample.
4. No changes were made to DirectSampler, RecorderLease, RecordingService or
   SessionStore; verify REC / STOP and Quick Settings still work in repeated runs.
5. Verify **CM HUD** / **CM REC** labels and concise secondary statuses are
   visible in the Odin Quick Settings shade. Full accessibility text remains.

## Build & database
- versionCode 36, versionName 0.9.7.3-interaction-polish-alpha.
- SQLite schema remains v6; migration and stored session integrity unchanged.
- Both debug and optimized-test CI builds must succeed.
- The optimized-test APK is R8-minified and may be signed using a CI temporary
  debug key. Preserve data and confirm signing compatibility before reinstalling.

## Deferred — not part of v0.9.7.3
- New HUD designs and theme variants.
- DUKO Report export (concept only until standard analysis is finalized).
- Any new FPS claims, privileged/root collection, heavy blur or looping effects.
