#!/usr/bin/env bash
# Fails when appVersion in gradle.properties is not SemVer (X.Y.Z or X.Y.Z-rc.N). Prints the derived version code.
set -euo pipefail
cd "$(dirname "$0")/.."
version="$(sed -n 's/^appVersion=//p' gradle.properties | tr -d '[:space:]')"
if ! [[ "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-rc\.([0-9]+))?$ ]]; then
  echo "appVersion '$version' is not MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-rc.N" >&2; exit 1
fi
release="${BASH_REMATCH[5]:-99}"
code=$(( (BASH_REMATCH[1] * 10000 + BASH_REMATCH[2] * 100 + BASH_REMATCH[3]) * 100 + release ))
echo "version $version (code $code) ok"
