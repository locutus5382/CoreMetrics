#!/usr/bin/env bash
# The emulator action executes individual script lines in separate shells.
# Use this ONE script process so the Gradle result survives cleanup commands.
set -uo pipefail

if [ "$#" -ne 1 ]; then
  echo "Expected an Android instrumentation test-class list" >&2
  exit 2
fi
classes="$1"
mkdir -p app/build/outputs/ci-emulator
adb logcat -c >/dev/null 2>&1 || true

# Preserve the REAL test status, even if best-effort logcat fails.
set +e
timeout --signal=TERM --kill-after=20s 20m \
  gradle :app:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=$classes" --no-daemon
test_status=$?
set -e

timeout 12s adb logcat -d -t 3000 \
  > app/build/outputs/ci-emulator/logcat.txt 2>&1 || true
adb devices -l > app/build/outputs/ci-emulator/devices.txt 2>&1 || true

printf 'Android instrumentation exit code: %s\n' "$test_status"
exit "$test_status"
