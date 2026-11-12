# CoreMetrics development roadmap

## Alpha 1 — accurate and dependable recording
- User-initiated Start/Stop, distinct `:monitor` foreground service and prominent state indication.
- No telemetry during idle; fixed-cadence sampling at 1, 2 or 5 seconds.
- Correct units and sensor provenance; null is not zero.
- Persistent on-device history; bounded export with CSV formula-injection guards.
- System-memory estimate distinguished from Eden-specific memory.
- Session labels Stock, ClusterTune or Custom are manual declarations, never verified tuner state.
- Capture interrupt/crash state, startup and stop outcomes.

## Alpha 2 — diagnostic measurement build (v0.2)
- Battery net watts from public Android APIs; treat charging and bypass readings as non-comparable.
- Optional SurfaceFlinger per-layer frame probe, 15-second cadence, timeout, no generic privileged shell. If denied, explain why in Diagnostics.
- Recorder journal and own process exit history to distinguish failed Start, process kill and user Stop.
- UI Instrument Glass with telemetry cards and 60-second sample history.

## Alpha 2 — real analytics
- Controller-friendly four-page layout: Live, Sessions, Compare, Settings.
- Charts of existing validated metrics, zoomable session curves, side-by-side A/B mean and peak delta.
- Fixed-window comparison, duration/time alignment, trim warmup/loading and note whether game scenes match.
- Clearly distinguish dashboard samples from actual per-frame metrics.

## Alpha 3 — measurement enhancement
- Android public-API battery current/voltage (battery-side watts, not CPU/GPU package power); detect unsupported/charging/bypass.
- Optional read-only, privileged Odin-only **experimental** frequency limits/FPS layer discovery, kept out of the Play build until legal/platform policy review. NO `oom_score_adj`, `chmod`, frequency writes or generic shell.
- Validate GPU clock with independent device readbacks.
- Actual per-layer FPS and frame time if available; only then calculate mathematically correct frame-level 1%/0.2% lows.

## Alpha 4 — HUD Studio
- Lightweight, draggable user-triggered HUD with Nano, Dual, Graph, and Corner modes; explicit overlay permission rationale.
- HUD draws only sampled values; no duplicate sampler or persistent animation loop.
- No unsupported FPS or wattage widgets shown as if working.

## Release gates
- Play API 36 behavior and background start restrictions.
- Play Console specialUse FGS declaration and review (approval not guaranteed).
- Privacy policy and Data safety form verified.
- Crash/LMK launch A/B tested with Eden on 8 GB device.
- No simultaneous monitoring with O2P for controlled comparisons.
- Independent IP/provenance and third-party license audit.
