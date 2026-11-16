# CoreMetrics v0.9.7.5 — compact HUD and reliability acceptance

**Development test candidate.** Same Android app ID and SQLite schema 6 as
v0.9.7.4. No new sensors, GPU driver data, game FPS collection, or recording
service/lease changes. Version code 38. Test on AYN Odin 2 Portal (Android 13).

## Visual acceptance: bright and dark games

- Launch NFS Hot Pursuit Remastered / Eden with a dark scene and a bright
  menu/map. Check that the compact horizontal HUD reads clearly at small,
  medium, and large metric sizes and 30–90% background opacity.
- CPU and GPU values are on the LEFT, a small two-level control cluster
  (REC/STOP over ⇥, lock, standby close) is on the RIGHT. There is no unused
  half-width bottom row, and no in-game settings gear.
- Active recording defaults to numeric temperatures, CPU/GPU loads when
  enabled, RAM and battery-terminal watts, with no invented game FPS.
- **No OS NONE / OS OK pill.** A 4 dp alert accent appears only if Android
  reports genuine device-wide LIGHT+ thermal status or a valid headroom
  advisory. 0/NONE and unavailable headroom do not imply no throttling.
  The bar is NOT colored by absolute CPU degrees.
- Test the redesigned HUD Studio preview. It is illustrative, not a
  pixel-identical measurement nor a second telemetry poller.

## Locked state and touch passthrough — safety critical

1. Start REC, then tap LOCK.
2. REC, collapse, close and other controls disappear. The telemetry and a
   separate 48 dp unlock target remain visible; no transparent button row
   should block the game.
3. Tap outside the 48 dp unlock target in-game; gameplay touches should pass
   through. Android 12+ overlay policy still applies.
4. Tap the unlock target once; interactive controls must reappear. Repeat
   lock/unlock 10 times, near both screen edges, after HUD show/hide and while
   the app goes to background.
5. If WindowManager rejects creation of the independent unlock window, locking
   must fail safely rather than leaving an untouchable overlay.
6. Verify there is exactly ONE owned HUD and ONE unlock target after any
   service crash/recovery; test after active REC recovery and READY recovery.

## Corner parking and deliberate collapse

- Drag expanded HUD towards top-left, top-right, left and right edges:
  it snaps approximately 4 dp from the bezel; it does **not** collapse.
- Drag to the center and leave it: HUD remains expanded and positioned.
- Tap ⇥ deliberately while unlocked. It collapses into a narrow
  left/right/top edge tab according to the parked position.
- In standby, tab reads CM / READY with cyan border.
- In REC, tab reads `● R` plus elapsed time, with a red border. Timer
  updates at recorder sample cadence (2s/5s), without a separate ticking loop.
- Tap tab: expand to the previously parked position, not the middle of the
  game. Verify it never leaves an invisible WindowManager touch rectangle.
- Verify 150 ms one-shot dock/expand animation still finishes even with
  rapid show/hide/disarm.

## Regression / performance

- REC/STOP, CM HUD and CM REC tiles must work at 2s and 5s sample cadence.
- Standby recovery remains the one-shot behavior introduced in v0.9.7.4.
- Compare `RECORDER_TIMING` p95 collector, db, hud, total and start_late
  after the same track. No additional telemetry polling is introduced.
- Verify app Settings correctly changes HUD metrics, opacity, layout and
  scale; changes no longer require a live in-game drawer.
- Saved sessions, CSV and scorecard exports remain unchanged.
- Test all four existing main app themes; no theme redesign in this build.
- CI JVM tests include pure HUD corner geometry; emulator/window overlap,
  accidental tapping and real GPU/CPU performance require Odin device testing.

## Known limitations

- The magenta Eden FPS/frametime/Process RAM readout in screenshots is supplied
  by Eden, not CoreMetrics. No game FPS source is integrated here.
- Android OS thermal status NONE cannot prove an absence of throttling.
- The narrow screen geometry keeps all controls on-screen, but user-chosen
  HUD settings with many metrics may wrap to additional lines.
- The preview in Settings shows representative values only.
