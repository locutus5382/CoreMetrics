# Third-party dependencies and notices

The application's **own source** is proprietary under LICENSE. That notice does not supersede the licenses of Android platform components, Kotlin, AndroidX, Jetpack Compose, Gradle, kotlinx.coroutines, JUnit, and other separately distributed dependencies.

The project currently relies on ordinary Maven build dependencies (see app/build.gradle.kts), primarily Google Android/AndroidX and JetBrains libraries distributed under their respective permissive/open licenses, plus JUnit for tests. Before a Play release, generate a complete dependency and license inventory from the exact resolved release build and include legally required notices.

No source from ClusterTune, PULSE, DroidPerf, or O2P Control Center has been copied into this independently implemented CoreMetrics source tree. Studying their publicly documented functionality does not itself authorize copying their implementations.

IMPORTANT: If independently authored O2P code is reused in CoreMetrics later, first confirm copyright ownership and any GPL/MIT obligations for that exact file. A different copyright header alone is not a relicensing grant.
