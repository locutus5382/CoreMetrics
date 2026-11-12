# CoreMetrics privacy statement — draft (2026-11-11)

CoreMetrics is intended to work fully offline. The current app contains no `INTERNET` permission, accounts, advertisements, analytics, or developer-controlled remote collection.

While you explicitly record a session, CoreMetrics stores device telemetry, timestamps, user-provided session/game labels, and — only if Usage Access is granted — the foreground Android application package name, in app-private local SQLite storage. This can include package identifiers of other apps you use during the session.

The app does not transmit this information. You may use the Android document picker to export a CSV to a location you choose. Files shared by the user outside CoreMetrics are governed by the chosen destination's privacy practices.

The app requires notifications to display ongoing recording status and an accessible Stop action. Optional Usage Access is used solely for app attribution; recording works without it. Optional display-over-other-apps permission may be requested from system Settings for an explicitly user-enabled floating HUD. In standby it shows a small READY/REC control but does not read sensors or write telemetry. Once the user taps REC, the overlay displays the existing session readings without introducing an additional sampler. The standby service retains an ongoing foreground notification until the user closes it.

Deleting the app normally removes its local database. Independently exported CSV files are not automatically deleted by the app. For privacy requests or support, use the contact information supplied by the publisher's eventual Google Play listing.

Before publishing, review this notice against the actual release and all third-party SDK behavior; submit an accurate Google Play Data safety form and provide a publicly accessible privacy policy URL.
