# CoreMetrics v0.6 — Odin 2 Portal test protocol

## Parallel-install Preview APK

GitHub Actions builds two v0.6 APKs:
- **app-preview.apk** (`com.locutus5382.coremetrics.preview`), labelled **CoreMetrics Preview**. Recommended for testing simultaneously with v0.5; it has a separate DB and private monitor provider / Quick Settings service. Import v0.5 CSVs inside Preview.
- **app-debug.apk** (`com.locutus5382.coremetrics`) is an in-place upgrade candidate only when signing matches. CI runners can produce different debug signing keys; do not uninstall v0.5 just to install it.

## Installation and preservation
- Recommended: retain v0.5 and install **CoreMetrics Preview** in parallel. The Preview app has a new package and private database; it will **not** automatically inherit v0.5 sessions. Export v0.5 CSVs and import them into Preview. Keep only **one CoreMetrics HUD/recorder active at a time** so the two monitors do not compete or block gameplay touches.
- Alternative: regular v0.6 debug APK replaces v0.5 under the same package if signing keys match; versionCode 6 cannot usually be downgraded to 5 without reinstall/data loss. Export before considering an upgrade or uninstall.
- For either APK use the **latest successful** GitHub Actions run. Verify normal recording, import/export and saving independently in Preview. If using the regular APK as an upgrade, verify v0.5 SQLite history survives.

## HUD
1. Launch standby, verify **no recorder samples** before in-game REC. Open Eden, start REC, let at least 30 samples accumulate.
2. Choose HUD Pro layout; enable CPU %, GPU %, CPU temp, FPS and sparklines. Exactly those that have actual data should display corresponding graphs; FPS remains unavailable on the Portal without a verified source. Blinking REC indicator works.
3. Open ⚙ drawer; toggle CPU/GPU visibility, graph display, power and opacity. All controls act on the same overlay. Confirm preference persistence after close/restart.
4. Drag the HUD to within 16dp of left and right edges; check 12dp colored bezel tab and 44dp click target, tap to expand. Check performance in Eden. Tab color encodes thermal **trend** (not thermal safety).
5. Lock while recording; verify pass-through touch on game. Unlock using the notification or Studio, then tap STOP. If a touch is blocked check logcat for `Untrusted touch due to occlusion`. Do not weaken Android touch protections.
6. Confirm STOP clears sparklines and returns to standby without sampling, and Quick Settings stays REC/STOP.

## CSV and comparison
1. Import a known-good exported short session via Sessions > Import CSV. Verify one `IMPORTED` entry and identical sample count, raw temperatures, power and observed policy3/policy7 limits; the original stays intact.
2. Import historical Hades `CoreMetrics_2 (2).csv` if available. Check Stock / Medium UC / Large UC phases based on actual observed frequency ceilings. No attribution to ClusterTune without independent evidence.
3. In Compare, choose A/B from recorded/imported sessions. Toggle CPU temp, GPU temp, CPU/GPU load, power and FPS. Drag chart gold cursor and use slider via controller. Check synchronized **elapsed time** display, not game-event synchronization. Long gaps and an ended run must show unavailable.
4. Toggle GPU ≥15% gameplay filter; compare the same track/scene and exclude loading screens. Check that null power/FPS is not reported as zero.
5. Try malformed/truncated CSV. Expect clear error, no new partial session. Try a large file beyond 12,000 rows; the import must roll back.

## Experimental FPS Lab
- This build runs **zero** PServer commands. It cannot claim actual FPS on Odin. The isolated provider's mocked-host parser and gate tests demonstrate failure behavior only.
- Before connecting hardware transport: verify an actual privileged read-only PServer host's Binder UID, nonce challenge, firmware support, and exact allowed SurfaceFlinger requests; calibrate output timestamp column using independent known 30/60 FPS scenes. Never guess the column or use UI refresh rate. Confirm overhead does not harm Eden.
- Keep the experiment off if any proof/permission/layer check fails. No 1% lows or frame stability until contiguous multi-minute frame data can be validated.

If the Portal freezes or Eden becomes unstable, stop testing and export the Android bugreport and CoreMetrics lifecycle logs.
