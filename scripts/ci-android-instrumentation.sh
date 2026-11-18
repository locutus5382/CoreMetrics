#!/usr/bin/env bash
# The emulator action executes individual script lines in separate shells.
# Use this ONE script process so the Gradle result survives cleanup commands.
set -uo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "Expected Android test-class list and optional debug|heimdallTrial variant" >&2
  exit 2
fi
classes="$1"
variant="${2:-debug}"
case "$variant" in
  debug)
    gradle_task=":app:connectedDebugAndroidTest"
    test_build_property=()
    ;;
  heimdallTrial)
    gradle_task=":app:connectedHeimdallTrialAndroidTest"
    test_build_property=("-PheimdallTrialInstrumentation=true")
    ;;
  *)
    echo "Invalid instrumentation variant: $variant" >&2
    exit 2
    ;;
esac
mkdir -p app/build/outputs/ci-emulator
adb logcat -c >/dev/null 2>&1 || true

# Preserve the REAL test status, even if best-effort logcat fails.
set +e
timeout --signal=TERM --kill-after=20s 20m \
  gradle "$gradle_task" "${test_build_property[@]}" \
  "-Pandroid.testInstrumentationRunnerArguments.class=$classes" --no-daemon
test_status=$?
set -e

timeout 12s adb logcat -d -t 3000 \
  > app/build/outputs/ci-emulator/logcat.txt 2>&1 || true
adb devices -l > app/build/outputs/ci-emulator/devices.txt 2>&1 || true

printf 'Android instrumentation exit code: %s\n' "$test_status"
exit "$test_status"
