#!/usr/bin/env bash
# Makes room for the emulator only if needed, and fails fast: the emulator action never detects a dead emulator, it
# only waits out emulator-boot-timeout.
# usage: ensure_free_disk.sh NEED_MB     (API_LEVEL from the environment, for the error message only)
# One threshold (kotlin-unit-tests.yml passes 9000 MB) for every API level: on API >= 24 the emulator forces a 6 GiB
# minimum data partition and wants 1.2x that free when creating it (~7.4 GB) plus margin; API 23's smaller need is
# covered too. Below it, delete only the preinstalled trees this job never uses that are fastest to delete per GB
# (ghcup, swift, powershell: ~1 s each); dotnet and CodeQL are slow (~7 s, ~5 s) and not touched. Conditional, so
# runners with big disks skip it. Not touched either: the Android SDK (/usr/local/lib/android), the JDK, the rest of
# /opt/hostedtoolcache.
set -eu

[ $# -eq 1 ] || { echo "usage: $0 NEED_MB" >&2; exit 2; }
need_mb=$1

free_mb() { df -Pm "$HOME" | awk 'NR==2 {print $4}'; }

findmnt -no OPTIONS /
df -h /
have_mb=$(free_mb)
if (( have_mb < need_mb )); then
  echo "${have_mb} MB free, ${need_mb} MB needed: removing unused preinstalled toolchains"
  for dir in /usr/local/.ghcup /usr/share/swift /usr/local/share/powershell; do
    start=$SECONDS
    sudo rm -rf "$dir"
    echo "  rm $dir: $(( SECONDS - start )) s"
  done
  df -h /
  have_mb=$(free_mb)
fi
if (( have_mb < need_mb )); then
  echo "::error::Only ${have_mb} MB free after installing the emulator, the emulator needs ${need_mb} MB (API ${API_LEVEL:-unknown})"
  exit 1
fi
