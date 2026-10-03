#!/usr/bin/env bash
# Creates the release signing key and prints what to put in GitHub and F-Droid. Run it on YOUR machine:
# the key must never be generated, pasted or stored anywhere else (not in the repo, not in a chat).
set -euo pipefail

out="${1:-ultimatefiles-release.jks}"
alias="${KEY_ALIAS:-ultimatefiles}"

command -v keytool >/dev/null || { echo "keytool not found: install a JDK (17 or newer)." >&2; exit 1; }
[ ! -e "$out" ] || { echo "$out already exists; refusing to overwrite a key." >&2; exit 1; }

read -r -s -p "Choose a password for the keystore (min. 12 characters): " pass; echo
[ "${#pass}" -ge 12 ] || { echo "Too short." >&2; exit 1; }
read -r -s -p "Repeat it: " again; echo
[ "$pass" = "$again" ] || { echo "The passwords differ." >&2; exit 1; }

# One password for store and key keeps the four GitHub secrets simple (PKCS12 requires them to match anyway).
keytool -genkeypair -v -storetype PKCS12 -keystore "$out" -alias "$alias" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=UltimateFiles, O=qtekfun" \
  -storepass "$pass" -keypass "$pass"

echo
echo "=== Back this file up NOW (password manager + offline copy). Losing it means no more updates for existing users. ==="
echo "Keystore: $(pwd)/$out"
echo
echo "=== GitHub: Settings > Secrets and variables > Actions > New repository secret ==="
echo "SIGNING_KEYSTORE_BASE64  = (the single line printed below)"
base64 < "$out" | tr -d '\n'; echo
echo "SIGNING_STORE_PASSWORD   = the password you just typed"
echo "SIGNING_KEY_ALIAS        = $alias"
echo "SIGNING_KEY_PASSWORD     = the same password"
echo
echo "=== F-Droid: AllowedAPKSigningKeys (SHA-256 of the certificate, lower case, no colons) ==="
keytool -list -v -keystore "$out" -alias "$alias" -storepass "$pass" \
  | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | tr -d ':' | tr 'A-F' 'a-f'
