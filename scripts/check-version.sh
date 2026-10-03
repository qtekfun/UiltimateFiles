#!/usr/bin/env bash
# Fails when version.properties is malformed or versionCode does not follow versionName.
set -euo pipefail
cd "$(dirname "$0")/.."
name="$(sed -n 's/^versionName=//p' version.properties | tr -d '[:space:]')"
code="$(sed -n 's/^versionCode=//p' version.properties | tr -d '[:space:]')"
if ! [[ "$name" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "versionName '$name' is not MAJOR.MINOR.PATCH" >&2; exit 1
fi
expected=$(( BASH_REMATCH[1] * 10000 + BASH_REMATCH[2] * 100 + BASH_REMATCH[3] ))
if [ "$code" != "$expected" ]; then
  echo "versionCode is '$code' but $name needs $expected" >&2; exit 1
fi
echo "version $name ($code) ok"
