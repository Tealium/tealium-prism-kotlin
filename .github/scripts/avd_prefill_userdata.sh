#!/usr/bin/env bash
# Pre-initialises the AVD's data partition so the emulator skips its first-time setup. Run from the emulator action's
# pre-emulator-launch-script in .github/workflows/kotlin-unit-tests.yml (as `|| true`: it never fails the step).
#
# If avdmanager copied a full filesystem template (>= 800 MB, the default data partition size) to userdata.img, it is
# renamed to userdata-qemu.img. With no userdata-qemu.img the emulator's first-time setup applies its 6 GiB floor
# (API >= 24) and a free-space gate, copies the template byte by byte, runs e2fsck and resizes it (~30 s on API 23,
# whose template is a full 2 GiB ext4); an existing userdata-qemu.img counts as an initialised data partition and skips
# all of that (the same convention AVD caches rely on). The size condition keeps images that ship a 1 MiB stub plus a
# data/ dir (API >= 28) on the normal path. The AVD dir is created fresh per job (force-avd-creation defaults to
# true). disk-size must stay unset or <= the template size: a larger one makes the emulator e2fsck and grow the image.
set -u

full_template_min_bytes=$(( 800 * 1024 * 1024 ))
d="${ANDROID_AVD_HOME:-$HOME/.android/avd}/test.avd"

if [ "$(stat -c %s "$d/userdata.img" 2>/dev/null || echo 0)" -ge "$full_template_min_bytes" ] && [ ! -e "$d/userdata-qemu.img" ]; then
  mv "$d/userdata.img" "$d/userdata-qemu.img" && echo "userdata.img is a full template: renamed to userdata-qemu.img"
else
  echo "userdata template is a stub or userdata-qemu.img exists: normal first-time setup"
fi
