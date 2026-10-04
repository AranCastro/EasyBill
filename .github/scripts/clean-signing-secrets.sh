# Sourced by the release workflow. Secrets pasted from a phone or a note often carry a
# line break, a non-breaking space or a zero-width character before or after the value;
# keytool and apksigner then report "keystore password was incorrect". Strip these from
# both ends, then refuse any password that still has a non-ASCII character in it.
clean_secret() {
  printf '%s' "$1" | perl -CSD -0777 -pe 's/\A[\s\x{200B}\x{200C}\x{200D}\x{2060}\x{FEFF}]+//; s/[\s\x{200B}\x{200C}\x{200D}\x{2060}\x{FEFF}]+\z//'
}
STORE_PASSWORD="$(clean_secret "$STORE_PASSWORD")"
KEY_PASSWORD="$(clean_secret "$KEY_PASSWORD")"
KEY_ALIAS="$(clean_secret "$KEY_ALIAS")"
export STORE_PASSWORD KEY_PASSWORD KEY_ALIAS
for name in STORE_PASSWORD KEY_PASSWORD; do
  if printf '%s' "${!name}" | LC_ALL=C grep -q '[^ -~]'; then
    echo "RELEASE_$name has a character that is not plain ASCII inside it. Type it again in" >&2
    echo "Settings > Secrets and variables > Actions (do not paste from a formatted note)." >&2
    exit 1
  fi
done
