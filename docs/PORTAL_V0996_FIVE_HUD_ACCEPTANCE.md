# CoreMetrics v0.9.9.6 — Five-HUD visual overhaul

Draft alpha, versionCode 49. SQLite v9 and report format v0.9.9 unchanged.
This branch follows v0.9.9.5 PR #43; it supersedes the three rejected
HUD presentations. Do NOT ship or merge before Odin 2 Portal acceptance.

## Five actual HUD modes, selected in HUD Studio

1. **The Striker (default)**: angular chamfered translucent cyan frame,
   split into thermal and utilization/resource areas. Right-side REC/STOP,
   drag-on-metrics and vector lock. Game identity is intentionally NOT
   guessed: show CM / LIVE rather than inventing a title from the OS.
2. **The Ghost HUD**: no opaque frame or boundary, shadowed inline telemetry.
   Inline compact REC/STOP and lock. No always-visible dock control.
3. **The Horizontal Strip**: translucent landscape cinema strip, inline
   provenance-labelled readings and REC/STOP; no forced huge app-wide bar.
4. **The Compact Card HUD**: compact tactical panel with a title, independently
   drawn THERMALS / UTILIZATION / RESOURCES cells, and right REC/STOP,
   lock, anchor/dock controls.
5. **DUKO Lens**: sharp rectangle with engineering corner brackets,
   monospaced key/value readings, white text except a CPU advisory orange
   at >=42 C or crimson at >80 C, and a red elapsed-time STOP beacon;
   text-based [ LOCK ] control. These numbers are display advisories only,
   not certified critical temperatures or evidence of throttling.
   An opaque-glass fallback uses tinted alpha and corner framing, not
   fake background screenshots. True full-screen blur-behind is deliberately
   avoided; on vendor Android ROMs it can blur the whole game and increase
   GPU overhead. Isolated 16px live backdrop blur is not yet implemented.

All modes support previously selected seven telemetry flags, scaling,
transparency, strict standby (no sampling until REC), start/stop, locking,
small edge tab, and no verified FPS claims. Three recording/data paths and
report FIFO remain unchanged. Live HUD uses one WindowManager root,
plus the small independent unlock handle only while main HUD is locked.
One UI sample update every existing 2s/5s recorder tick. The STOP action
always uses the existing same-session finalize callback.

## Swipe-to-edge

- Swipe a METRIC zone >=64dp horizontally toward a screen edge and release
  within 26dp of the bezel. An accidental short or mostly vertical drag
  cannot auto-collapse.
- REC/STOP, LOCK and standard controls do not initiate drag. Locking disables
  dragging, swiping, docking and allows game taps beneath the telemetry.
- Expanded state saves position. Collapsed one-window target shrinks to
  44 x 44dp, not a full-screen or giant invisible touch area. Lens target
  paints a 2dp illuminated line with a red status dot; others use a small
  recording badge. Tap the target to expand. Physical visual size can be
  ~2px while Android touch target is 44dp for accessibility.
- A single pre-existing 650ms indicator callback may pulse during REC,
  including while collapsed. No independent sensors, polling or game data.
- The separate locked 48dp unlock chip remains a touchable recovery control;
  no full-window locked overlay can intercept game touches.

## UI and safety acceptance

1. All five themes persist across UI/monitor processes/Intent extras.
   Old IDs pill -> Striker, glass_card/pro -> Compact Card,
   ribbon -> Horizontal Strip. Unknown -> Striker.
2. Select 0, 1, 4 and all 7 metrics; no hidden or fictitious readings.
   CPU/GPU labels mean system telemetry, not per-game FPS. RAM is system used,
   not the emulator's process RAM.
3. Inspect readability and obstruction in Mario Kart/Eden in both bright
   and dark scenes, 1648x928 landscape, size small/normal/large, opacity
   .30/.57/.90, 2s and 5s sampling.
4. Test tap REC, tap STOP (saved single final session), Quick Settings REC/HUD,
   lock/unlock, drag, fling to both sides, edge expansion and permanent close.
5. Verify no duplicate WindowManager view and no separate sensor loop. Export
   DUKO single/comparison reports and confirm FIFO/cancellation unaffected.
6. Check PSS of monitor and UI separately, CPU render time, battery overhead
   and failure recovery during Eden launches and process pressure.
7. Evaluate game interaction while locked, including Android 12+ overlay
   obscuring-touch rules; the accessible unlock chip must always remain.
8. If CI/JVM or emulator tests fail, do not mark a release candidate as ready.

## Non-goals

No game-name guessing, no fabricated 60FPS readout, no hardware underclocking,
no external assets or licensed fonts, no real-time screenshots, no global
background blur, no data schema changes or unsolicited reports.
