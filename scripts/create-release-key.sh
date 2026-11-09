#!/usr/bin/env bash
# Creates the release signing key once and prints the values to store as GitHub secrets.
# KEEP release.jks AND ITS PASSWORD FOREVER (e.g. in a password manager): an APK signed with
# a different key cannot update an installed one, users would have to uninstall first.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -e release.jks ]; then echo "release.jks already exists, refusing to overwrite." >&2; exit 1; fi
read -rsp "Keystore password (min 6 chars): " PASS; echo
keytool -genkeypair -v -keystore release.jks -storetype PKCS12 -alias hypernotifyfix \
  -keyalg RSA -keysize 4096 -validity 36500 -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=HyperNotifyFix, O=ds-kell"

printf 'storeFile=release.jks\nstorePassword=%s\nkeyAlias=hypernotifyfix\nkeyPassword=%s\n' "$PASS" "$PASS" > keystore.properties
base64 < release.jks | tr -d '\n' > release.jks.base64

cat <<EOF

Done. release.jks and keystore.properties are git-ignored.
Add these repository secrets (GitHub → Settings → Secrets and variables → Actions):
  KEYSTORE_BASE64   = contents of release.jks.base64 (then delete that file)
  KEYSTORE_PASSWORD = the password you typed
  KEY_ALIAS         = hypernotifyfix
  KEY_PASSWORD      = the password you typed
EOF
