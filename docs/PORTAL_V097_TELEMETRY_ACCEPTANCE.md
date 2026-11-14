# CoreMetrics v0.9.7 — Phase 2 Odin 2 Portal acceptance

**Status:** Development/test candidate; NOT merged to main or cleared for Play.

## Upgrade safety
- Export valuable sessions before changing builds. Use the same signing key to update the canonical package and preserve local SQLite.
- Keep v0.9.6.6 source as the rollback baseline. Do not install a different package variant in parallel with the unified app without reviewing Quick Settings tiles and overlay permissions.
- v0.9.7 uses SQLite schema 6. Downgrading to a schema-5 APK in place is not supported: export/back up sessions first; do not uninstall without preserving them.

## HUD thermal dot
- Begin in READY: no active telemetry reads or warning dot.
- Tap REC: dot becomes **white** for normal OS reading, **amber** when Android reports LIGHT/MODERATE or observed 10-second headroom >=0.85, **red** for SEVERE+ or headroom >=1.0. **Grey** means unavailable, not normal.
- Headroom is advisory: the early amber threshold is a UI choice, not an Android throttling boundary; 1.0 is the platform's severe reference. Some ROMs always return THERMAL_STATUS_NONE.
- Confirm changing status does not create duplicate HUDs, affect REC button, increase view count, or read sensors independently of the active recorder.
- The small dot is for the *device*, not CPU/GPU individually; do not interpret thermal status as proof of which frequency governor throttled.

## New sensor availability
1. Settings → process-exit and lifecycle log: inspect existing one-shot read-only capability snapshot.
2. Record a 5-second-cadence session and export CSV. Confirm new headers: thermal_status, thermal_headroom, swap_used_gib, mem_psi_some10, sample_duration_ms, sample_elapsed_ms.
3. Confirm a missing/denied PSI or headroom source exports blank cells, not 0 or NaN, and does not stop recording.
4. Headroom values should be recorded at most once every 10 seconds; intermediate rows should be blank.
5. Check CPU/GPU °C still use existing sysfs sensor selection. Validate new signals as device-supported only after real gameplay observations.

## Recording integrity and exports
- Complete an uninterrupted 2–3 minute test at 5 seconds; open its Session Summary and confirm an appropriate Reliable result.
- Reproduce an interruption/gap if possible, then confirm Partial/Interrupted appears without discarding previously saved rows.
- For a run with no CPU/GPU sensor, continuity can still be Reliable; missing sensors must not lower its continuity verdict.
- The technical panel shows optional OS metrics only when actually present. The TXT segment export includes the separate recording-integrity summary.
- Edit the game note and driver and confirm they update without reloading all sample rows.

## FPS and subjective smoothness
- There should be no FPS readout, FPS toggle, FPS chart selector or FPS Lab in the user-facing app.
- Legacy CSV fields remain importable/exportable for compatibility only. No new privileged process or SurfaceFlinger frame collection is started.
- Manually record subjective smoothness in a game note if helpful; CPU/GPU utilization and thermal status cannot by themselves establish frame pacing.

## Performance comparison
- Run a comparable 5-second baseline with HUD hidden and visible; record battery, thermal and system impact. The reported sampler duration excludes SQLite and HUD drawing, so it is *not* total app CPU overhead.
- Compare with v0.9.6.6 where possible. Monitor for LMK, duplicate overlays, session ID reassignment or improper standby resurrection after STOP.
- Only merge when CI succeeds **and** actual Odin acceptance meets these criteria.
