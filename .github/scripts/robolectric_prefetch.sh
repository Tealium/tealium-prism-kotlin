#!/usr/bin/env bash
# Downloads the Robolectric android-all-instrumented jars listed in .github/robolectric/ (update_robolectric_jars.py
# --versions checks the lists and prints the versions) into one flat folder. The unit tests run with
# -Drobolectric.dependency.dir pointing there (TealiumLibraryPlugin), so Robolectric never downloads.
# Used by .github/workflows/kotlin-unit-tests.yml (unit job).
#
#   bash .github/scripts/robolectric_prefetch.sh [DEST]   (DEST default: $ROBOLECTRIC_DEPENDENCY_DIR)
#
# Jars already in DEST are skipped. One curl fetches the missing ones in parallel to <jar>.part, with their .sha512 to
# <jar>.sha512; each .part matching its .sha512 is moved into place. Any other jar is an ::error:: annotation and fails
# the script. The total goes to the step summary.
#
# ROBOLECTRIC_REPO (the workflows pass the repository variable of that name; unset or empty = Maven Central,
# https://repo1.maven.org/maven2) is the Maven repository base URL. A lever if Central rate-limits CI, e.g. Google's
# Maven Central mirror https://maven-central.storage-download.googleapis.com/maven2. The .sha512 is fetched from the
# same host, so the chosen repository is trusted for integrity too. It doesn't change which jars are fetched: the jar
# names pin the versions.
set -euo pipefail

err() { echo "::error title=Robolectric jars::$*"; }
die() { err "$@"; exit 1; }
(( $# <= 1 )) || die "usage: $0 [DEST]"
dest=${1:-${ROBOLECTRIC_DEPENDENCY_DIR:-}} repo_base=${ROBOLECTRIC_REPO:-https://repo1.maven.org/maven2}
[[ -n $dest ]] || die "no destination: pass DEST or set ROBOLECTRIC_DEPENDENCY_DIR"
[[ $repo_base == https://* ]] || die "ROBOLECTRIC_REPO must be an https:// URL, got '$repo_base'"
repo=${repo_base%/}/org/robolectric/android-all-instrumented
versions=$(python3 -I "$(dirname "$0")/update_robolectric_jars.py" --versions)

mkdir -p "$dest"
echo "robolectric-jar repo=$repo"
SECONDS=0 jars=() missing=() jar_urls=() sum_urls=()
for version in $versions; do
  jar=$dest/android-all-instrumented-$version.jar url=$repo/$version/android-all-instrumented-$version.jar
  jars+=("$jar")
  [[ -s $jar ]] && continue
  missing+=("$jar") jar_urls+=(-o "$jar.part" "$url") sum_urls+=(-o "$jar.sha512" "$url.sha512")
done
failed=0
# Only "missing" can be empty (a warm cache): an empty array fails under set -u before bash 4.4, hence one guard.
if (( ${#missing[@]} )); then
  # --parallel-max 4: up to 4 transfers at once; curl 8.5 multiplexes them over one HTTP/2 connection (Central's
  # limits count GB and downloads per IP, not connections). If that proves slow, --http1.1 gives 4 connections.
  # --retry: each transfer on its own, from byte 0 (curl truncates the file); a huge Retry-After is bounded by the
  # step's timeout-minutes. --speed-*: a transfer below 100 kB/s for 30 s fails (curl exit 28) and is retried.
  # -w: one line per finished transfer (connections opened, speed); the \n before %{onerror} is required, because on
  # success %{onerror} drops everything after it, so only a failed transfer prints the second line.
  w='robolectric-jar %{filename_effective}: http=%{http_code} new_conns=%{num_connects} port=%{local_port}'
  w+=' %{size_download}B %{time_total}s %{speed_download}B/s\n'
  w+='%{onerror}robolectric-jar failed: %{url} (curl exit %{exitcode}: %{errormsg})\n'
  curl --parallel --parallel-max 4 -fL --no-progress-meter \
    --connect-timeout 20 --speed-limit 100000 --speed-time 30 --retry 5 --retry-all-errors --retry-delay 2 \
    -w "$w" "${jar_urls[@]}" "${sum_urls[@]}" || echo "robolectric-jar curl exit $?"
  for jar in "${missing[@]}"; do
    want=$(head -c 128 "$jar.sha512" 2>/dev/null | tr -dc 0-9a-fA-F | tr A-F a-f) || want="no .sha512"
    got=$(sha512sum "$jar.part" 2>/dev/null) || got="no .part"
    if [[ ${got%% *} == "$want" ]]; then mv -f "$jar.part" "$jar"
    else err "${jar##*/}: download failed or bad sha512 (got ${got:0:12}.., want ${want:0:12}..)"
      failed=$(( failed + 1 )); rm -f "$jar.part"; fi
    rm -f "$jar.sha512"
  done
fi
total=0
for jar in "${jars[@]}"; do [[ ! -s $jar ]] || total=$(( total + $(wc -c <"$jar") )); done
line="Robolectric jars: ${#jars[@]} ($(( total / 1000000 )) MB), ${#missing[@]} fetched, $failed failed, in ${SECONDS}s"
echo "$line"; echo "$line" >>"${GITHUB_STEP_SUMMARY:-/dev/null}"
(( failed == 0 )) || die "$failed jar(s) failed (listed above); the unit tests can't run without them"
