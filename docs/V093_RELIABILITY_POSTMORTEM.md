# CoreMetrics v0.9.3 — Hades / Odin 2 Portal reliability postmortem

## Observed evidence from 13 November 2026
- T30-associated Session #1: 247 samples in 20:30, CPU median 74.1 °C full session (72.1 °C cropped 14:20), GPU P50 54.1 °C cropped, battery terminal discharge 6.26 W cropped.
- StevenMXZ-associated Session #8: 173 samples in 14:20, CPU median 64.6 °C, GPU P50 50.9 °C, discharge 5.35 W. Same elapsed crop yields B−A +7.5 °C CPU P50 and +0.91 W discharge. Graphics drivers and fan/workload were NOT recorded in database tags; this order comes only from tester description.
- Android process exit history around 15:00 records MANY LOW_MEMORY exits from both UI and :monitor packages. A later session #8 had 173 evenly spaced samples and ended normally (STOP at 15:17:20); there is no log proving an LMK mid-session #8.
- Screenshot displays READY+REC HUDs simultaneously; their package/window identities are not included in the old journal. Android can display overlays from DIFFERENT installed app IDs. Service recreation in same PID and untracked failed WindowManager removals are additional same-package risks.
- Sessions #2–#7 contain 1–4 samples each, all marked COMPLETED. They may reflect manual retry/stop; the data alone cannot establish why each ended. Correct to mark them insufficient for comparative inference even if gap-free.
- No verified game FPS or equivalent-scene testing for both drivers; NOT valid to claim StevenMXZ alone improved game performance.

## Root-cause hypotheses (not established facts)
A. Multiple independently installed alpha packages (most likely if both were granted overlay and QS tiles).
B. Failed WindowManager cleanup after service teardown in the same :monitor process.
C. Quick Settings falsely interpreting a provider-status failure / fresh lease as idle, issuing a new ARM/START.
D. HUD remains visible over the app during the Activity startup visibility handshake.
The old journal does not discriminate A–D. The new journal includes package, PID, service instance, HUD root and unlock-window identity, and detach results.

## Implemented containment
- Process-global HUD registry: one owner across Service recreations in one PID, callback rebinding, tracked handles retained on removal failure, no second attached root if cleanup unresolved.
- Tri-state tiles: UNKNOWN/RECOVERABLE cannot initiate a fresh unrelated recording. UNKNOWN offers an explicit Try recover action, which is rejected without an existing validated lease; RECOVER reuses its session ID. START guard converts a fresh stale request to recovery.
- More responsive async foreground visibility handshake; no synchronous binder in onStart.
- All SAF exports preserve pending selection over Activity recreation; A/B driver labeling and PNG color consistency; honest short-duration quality ratings.

## Still unsupported
- Android cannot force LMK survival or guaranteed START_STICKY relaunch. The 90-second lease and two recovery attempts are best-effort; a force-stopped app is not equivalent to LMK.
- Process-local singleton **cannot coordinate different CoreMetrics package IDs**. To resolve this, turn off overlay permission for older variants and remove their QS tiles.
- No root/system privileges and no added network/Accessibility permission.
- Diagnostics remain observational, without verified FPS/W or equal gameplay-scene assurance.
- Device-level tests, exact driver tags, Android window-owner dumpsys, and real-world power/RAM profiling remain required.
