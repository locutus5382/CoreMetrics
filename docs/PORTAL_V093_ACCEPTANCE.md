# CoreMetrics v0.9.3 — Odin 2 Portal acceptance

This release responds to observed duplicate READY+REC HUD overlays, failed Quick Settings restoration, and LOW_MEMORY process exits during Hades testing. **Source review cannot prove that the old duplicate HUDs were both from the same APK; test package isolation first.**

## Before tests: isolate ONE overlay owner
- [ ] Export/copy CSVs and recorder diagnostics from old builds; their private SQLite databases are separate.
- [ ] In Android Settings → Apps → Special app access → Display over other apps, REVOKE overlay permission for **all other CoreMetrics test variants**, including v0.9.0–v0.9.2. Remove their old HUD and Record Quick Settings tiles.
- [ ] Optionally uninstall older test APKs after CSV backup; reboot Odin to clear existing overlay windows. Install **only v0.9.3 Themes** for the first pass. Do NOT start v0.9.3 Frosted simultaneously.
- [ ] Use Settings → Sessions → Edit metadata to tag the T30 or StevenMXZ graphics driver after each run. Stock is a CPU-cap label, not the Vulkan driver name.
- [ ] Verify new tile names show 'CM93' and the standby HUD says 'CoreMetrics v0.9.3'.

## Single-HUD ownership / lifecycle (P0)
- [ ] Start HUD Standby once, then click QS HUD, HUD_SHOW, lock/unlock/dock, and open/close the application repeatedly. One CoreMetrics overlay maximum (plus its **separate locked-unlock chip** when locked). No independent duplicate REC/STOP control.
- [ ] Rapidly tap both Quick Settings tiles while standby and while recording; no second active session, no duplicate HUD and no unintentional STOP from the HUD tile.
- [ ] Deliberately issue ARM while REC is active (e.g., stale Activity tap). It must act only as HUD_SHOW, must not reset the active lease/session ID, and a permission/attach failure must not terminate recording.
- [ ] Simulate same-process Service recreation with an existing attached HUD if possible; REC, lock and close callbacks must be rebound and the recording indicator must resume blinking.
- [ ] If the StatusProvider query fails, the tile offers **Try recover**: tap once. Only an existing validated lease may resume, never create a new session; without a lease it must decline safely.
- [ ] Close/disarm; journal should report HUD_REMOVE (or a truthful failed-removal state); try standby again. No old view remains interactive.
- [ ] Open CoreMetrics from Eden; its own overlay disappears promptly and does not cover STYLE/buttons. Return to game; overlay returns only if user visibility preference is enabled.
- [ ] If a duplicate appears, export Recorder Diagnostics immediately, before clearing it, and include the two visible HUDs and list of installed CoreMetrics packages.

## Real LOW_MEMORY recovery (P0)
- [ ] Start a 30–60-minute Eden session. Check lock/unlock, QS STOP and notification STOP, single saved session ID and gap-free 5s cadence under normal load.
- [ ] With a less valuable disposable test session, reproduce LMK if naturally possible; save Recorder Diagnostics and Android exit history. **DO NOT use Settings → Force stop as a simulation of LMK**: Android treats force-stop as a user action and can suppress restarts.
- [ ] When a fresh recorded lease exists after LMK, a Quick Settings tile must show Recover rather than creating a new session. Tap Recover once: it should resume the SAME session ID if allowed, mark absent time as a gap, and never create a second HUD. The lease expires after 90s and at most two automatic/explicit recoveries.
- [ ] If recovery is unavailable or denied, display an honest state; never mark an involuntarily interrupted run as COMPLETED, or synthesize samples during process downtime.
- [ ] Compare journal 'SERVICE_CREATED' package/PID/service object ID with 'HUD_ATTACH' root identity and 'HUD_REMOVE'. One service generation per process does not imply one WindowManager view unless removal is explicitly confirmed.

## File export and analytical regression (P1)
- [ ] Open each Save dialog (full CSV, benchmark TXT, segment TXT, segment CSV, A/B window TXT, PNG). Rotate/recreate Activity while picker is open, then save. Each must export the originally selected session IDs/range; cancel must do nothing.
- [ ] A/B PNG legend: **A cyan, B orange**, matching interactive chart. Check selected 00:00–14:20 Hades window against TXT statistics.
- [ ] Short 1–4-sample trouble-shooting runs are clearly marked INSUFFICIENT FOR COMPARISON even if 100% of the five seconds were recorded.
- [ ] Tag the T30 and StevenMXZ sessions explicitly and verify printed metadata and comparison cautions. Driver association is human tagging, not hardware detection.
- [ ] Verify v0.9.3 in selected TXT and PNG footers, source recording values and metadata unchanged.

## Exit gates / main
- [ ] Final exact-commit CI: unit tests, debug and both v093 APK builds, Android instrumentation compilation; device/emulator instrumented tests still require separate execution.
- [ ] P0: no duplicate interactive HUD within a single installed variant, no orphan window and no spurious REC from QS after status loss; no data loss.
- [ ] P1: documented export lifecycle and report correctness, no misleading comparison score.
- [ ] Only THEN consider a reviewed integration PR into `main`. Earlier draft PRs form a long stacked dependency chain; merging this one branch alone does not update `main` unless the earlier commits are included. Shipping requires production signing, canonical package ID, Play FGS declaration and privacy review.
