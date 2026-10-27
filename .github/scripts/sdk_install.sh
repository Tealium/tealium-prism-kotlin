#!/usr/bin/env bash
# Installs, in the background, what the emulator action would install itself: the same packages and channel (its
# channel input defaults to `stable` = --channel=0), so the action's own sdkmanager calls find them installed and skip
# them. Keep in sync with the action's api-level / target / arch / channel inputs in kotlin-unit-tests.yml.
#
# usage: sdk_install.sh start API_LEVEL | wait SECONDS     (AVD_TARGET and AVD_ARCH from the environment)
#   start  runs right after checkout so the download (network/IO-bound, 45-90 s) overlaps android-setup, module
#          detection and the APK build. It uses the runner's preinstalled JDK (setup-java hasn't run yet). The download
#          process outlives this step (the runner only cleans up leftover processes at job end) and writes its exit code
#          and end time to a done-file. Its output goes to a file, not the step's output pipes, which the runner would
#          otherwise wait on. It starts before the module list is known, so on a PR with no instrumented module it is
#          killed at job end (runner minutes, no wall-clock). The first lines log the runner's disk, cores and memory
#          before anything is installed.
#          The emulator is installed first, in its own sdkmanager call, then the platform and system image (the log
#          has an "emulator end" line between the two). Why: AGP reads $ANDROID_HOME/emulator/package.xml as a
#          configuration-cache input (FileExistsValueSource, SdkDirectLoadingStrategy), so the emulator must be in
#          place and stable before the APK build configures; the runner's preinstalled emulator is older and gets
#          replaced. If the emulator install fails, the second call is skipped; the done-file's exit code covers both.
#   wait   polls the done-file, bounded to SECONDS from the start of this call (i.e. after the APK build), and fails
#          at once if the download process is gone without a done-file, printing the sdkmanager log, so a hung
#          download fails clearly instead of running into the job timeout.
# The files stay under $RUNNER_TEMP: checkout may wipe the workspace. The log is uploaded as a CI artifact.
set -eu

pid_file="$RUNNER_TEMP/sdk-install.pid"
done_file="$RUNNER_TEMP/sdk-install.done"
log="$RUNNER_TEMP/sdk-install.log"

usage() { echo "usage: $0 start API_LEVEL | wait SECONDS" >&2; exit 2; }

fail() { echo "::error::$*"; cat "$log"; exit 1; }

start() {
  local api_level=$1
  df -h /
  nproc
  free -m
  local sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
  (
    rc=0
    { yes 2>/dev/null | "$sdkmanager" --licenses || true
      "$sdkmanager" --install emulator --channel=0 \
        && echo "emulator end $(date +%s)" \
        && "$sdkmanager" --install "platforms;android-${api_level}" \
          "system-images;android-${api_level};${AVD_TARGET};${AVD_ARCH}" --channel=0; } || rc=$?
    echo "$rc $(date +%s)" > "$done_file.tmp"
    mv "$done_file.tmp" "$done_file"
  ) > "$log" 2>&1 < /dev/null &
  echo $! > "$pid_file"
  echo "sdk start $(date +%s)"
}

wait_for_download() {
  local limit=$1 sdk_pid deadline
  sdk_pid=$(cat "$pid_file")
  deadline=$(( SECONDS + limit ))
  until [ -s "$done_file" ]; do
    kill -0 "$sdk_pid" 2>/dev/null || [ -s "$done_file" ] \
      || fail "The SDK package download exited without writing its exit code; its log follows"
    (( SECONDS < deadline )) \
      || fail "The SDK package download didn't finish within $limit s of the APK build ending; its log follows"
    sleep 1
  done
  local sdk_rc sdk_end
  read -r sdk_rc sdk_end < "$done_file"
  echo "sdk end $sdk_end (exit $sdk_rc), wait end $(date +%s)"
  (( sdk_rc == 0 )) || fail "sdkmanager failed (exit $sdk_rc), its log follows"
}

[ $# -eq 2 ] || usage
case "$1" in
  start) start "$2" ;;
  wait) wait_for_download "$2" ;;
  *) usage ;;
esac
