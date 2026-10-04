#!/usr/bin/env bash
# Creates the release signing key for Modern Kallaa Petti ONCE, outside the repository.
# Usage: bash tools/make-release-keystore.sh [folder]     (default folder: ~/kallaa-petti-signing)
#
# Keep two copies of the .jks file in two different safe places and write the password in a
# password manager. If the key is lost, no update can ever be installed over the installed app.
set -euo pipefail

OUT="${1:-$HOME/kallaa-petti-signing}"
KS="$OUT/kallaa-petti-release.jks"
ALIAS="kallaapetti"

mkdir -p "$OUT"
chmod 700 "$OUT"
if [ -e "$KS" ]; then
  echo "A key already exists at $KS. It is not replaced." >&2
  exit 1
fi

read -r -s -p "Choose a keystore password (at least 12 characters; it is also used for the key): " PW
echo
read -r -s -p "Type it again: " PW2
echo
if [ "$PW" != "$PW2" ] || [ "${#PW}" -lt 12 ]; then
  echo "The passwords differ or are shorter than 12 characters." >&2
  exit 1
fi

# PKCS12 keystores use one password for the store and the key, so both are the same here.
keytool -genkeypair -v -keystore "$KS" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$PW" -keypass "$PW" \
  -dname "CN=Modern Kallaa Petti, O=Draran, C=IN"
chmod 600 "$KS"

echo
echo "Certificate fingerprint (keep this; the release check compares against it):"
keytool -list -v -keystore "$KS" -storepass "$PW" | grep -E "SHA256:|Valid from"

base64 -w0 "$KS" > "$OUT/kallaa-petti-release.jks.base64"
chmod 600 "$OUT/kallaa-petti-release.jks.base64"
echo
echo "Key file:    $KS"
echo "For GitHub:  $OUT/kallaa-petti-release.jks.base64  (value of RELEASE_KEYSTORE_BASE64; delete it after pasting)"
echo "Alias:       $ALIAS  (value of RELEASE_KEY_ALIAS)"
echo "Password:    use the same one for RELEASE_STORE_PASSWORD and RELEASE_KEY_PASSWORD"
echo "Now copy the .jks file to two separate safe places. Never commit it."
