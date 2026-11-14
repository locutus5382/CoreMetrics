# CoreMetrics v0.5 — Odin 2 Portal validation protocol

Draft only. Keep v0.4 and v0.5 as separate branches until device tests pass.

1. Install the CI-built v0.5 debug APK **over** existing CoreMetrics (same application ID and signing). Do not uninstall; verify existing SQLite sessions remain visible.
2. From HUD Studio, enable the standby pill. Confirm **no samples are written until REC**. Load Eden and a game; tap REC, play briefly, STOP, and check the completed session appears.
3. Test lock in standby: tap the small 🔓 icon, then attempt dragging/tapping the underlying game within the HUD footprint. While locked the overlay REC cannot work. Open the foreground notification and choose **Unlock HUD**, then verify REC/drag work again.
4. Repeat lock during REC; verify samples continue to be recorded and unlocked STOP finalizes the session. Confirm Quick Settings REC/STOP continues to work and does **not** silently change the lock state.
5. If taps under the lock fail, inspect logcat for `Untrusted touch due to occlusion`. Other overlays may combine opacity; never disable Android security protections to work around it.
6. Under HUD Studio Pro layout, warm up a game. Confirm raw CPU peak can exceed the smoother displayed temperature; rising/falling arrow should change over roughly 15 seconds and missing data should display —, not zero. Confirm exported CSV preserves raw temperatures.
7. For Hades, record at least 3 samples in each deliberate phase with a **known, observed** P3/P7 cap: Stock, ClusterTune Medium, ClusterTune Large. Review the single session as A in Compare. Confirm three cards, correct cap labels, duration, transition markers and baseline deltas.
8. Toggle Active 3D gameplay only (GPU ≥15%). Check that it changes *statistics* but does not merge separate time phases. Repeat a CPU ceiling after an underclock to verify distinct contiguous segments.
9. Check battery charging/bypass sessions: power projection should display unavailable. On battery, compare terminal discharge watts only; do not interpret it as wall draw. Verify no FPS value appears unless a genuine frame source is later added.
10. First smoke test Need for Speed briefly; if Eden or the Portal freezes, stop and collect a system bugreport. Do not assume this patch fixed a previous freeze.

## Known limitations

- Charts currently select sessions from CoreMetrics' private SQLite data; imported CSV files are **not yet supported**.
- Historical stock/underclock labels are recognized by observed cap values; they are not proof of which tuner changed them.
- Nominal 30.8 Wh divided by mean discharge watts is an optimistic model and not a verified battery-runtime measurement.
- SurfaceFlinger FPS, 1% low, frame stability, and GPUWatch parity require a separate verified data source.
