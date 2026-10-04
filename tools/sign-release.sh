#!/usr/bin/env bash
# Signs the release APK exactly as the GitHub release workflow does, so a local build
# and a published build are signed the same way.
# Usage: bash tools/sign-release.sh <keystore.jks> <version, e.g. 1.5.0> [alias]
# The keystore password is read from the prompt (or from KEYSTORE_PASSWORD).
set -euo pipefail

KS="${1:?Give the keystore file}"
VERSION="${2:?Give the version, for example 1.5.0}"
ALIAS="${3:-kallaapetti}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UNSIGNED="$ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
OUT="$ROOT/app/build/outputs/signed"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT="$SDK/build-tools/37.0.0"

[ -f "$UNSIGNED" ] || { echo "Run ./gradlew :app:assembleRelease first." >&2; exit 1; }
[ -x "$BT/apksigner" ] || { echo "Build-tools 37.0.0 not found at $BT" >&2; exit 1; }
if [ -z "${KEYSTORE_PASSWORD:-}" ]; then
  read -r -s -p "Keystore password: " KEYSTORE_PASSWORD
  echo
fi
export KEYSTORE_PASSWORD

mkdir -p "$OUT"
APK="$OUT/ModernKallaaPetti-v$VERSION.apk"
ALIGNED="$OUT/aligned.tmp.apk"
rm -f "$APK" "$ALIGNED"

# 16 KB page alignment is needed for phones that use 16 KB pages
"$BT/zipalign" -f -P 16 4 "$UNSIGNED" "$ALIGNED"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias "$ALIAS" \
  --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEYSTORE_PASSWORD \
  --min-sdk-version 26 --v4-signing-enabled false --out "$APK" "$ALIGNED"
rm -f "$ALIGNED"

"$BT/apksigner" verify --verbose --print-certs "$APK" | grep -E "Verifies|v2|v3|SHA-256"
"$BT/zipalign" -c -P 16 -v 4 "$APK" | tail -1
( cd "$OUT" && sha256sum "$(basename "$APK")" | tee "$(basename "$APK").sha256" )
cp "$ROOT/app/build/outputs/mapping/release/mapping.txt" "$OUT/mapping-v$VERSION.txt" 2>/dev/null || true
echo "Signed APK: $APK"
