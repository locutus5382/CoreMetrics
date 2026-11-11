# Google Play readiness — checklist (2026-11-11)

- [ ] Build and actually test targetSdk 36 + minSdk 31 (Android 13 on Odin, Android 16 emulator).
- [ ] Provide a signed release AAB and configure Play App Signing.
- [ ] Explain exact foreground service `specialUse` purpose, interruption impact and visible user action. Supply demo video and complete Play Console FGS declaration. Approval is not automatic.
- [ ] Provide clear permission rationale before requesting notifications or Usage Access. Do not request overlay until HUD is implemented.
- [ ] Validate any permissions and feature restrictions on target SDK 36.
- [ ] Confirm no network calls or third-party analytics; fill Data safety form accurately and host public privacy policy.
- [ ] Record device load and sampling gaps across repeated stock vs ClusterTune NFS runs.
- [ ] Verify independent runtime/service and no hardware writes. O2P remains separate.
- [ ] Verify third-party notices and all included assets/fonts/icons.
- [ ] Ensure consumer-facing wording never claims 'true FPS', 'system power', or 1% lows when not independently validated.
- [ ] Verify paid/freemium SKU, in-app billing policy and app signing when commercial model is selected.
