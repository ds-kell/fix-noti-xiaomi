#!/usr/bin/env bash
# Prints the four GitHub secrets for the existing signing key (signing/ + keystore.properties).
# Never create a new key: APKs signed with another key cannot update installed ones.
set -euo pipefail
cd "$(dirname "$0")/.."
prop() { sed -n "s/^$1=//p" keystore.properties; }
store=$(prop storeFile)
[ -f "$store" ] || { echo "Missing $store (restore it from your backup)" >&2; exit 1; }
out=signing/secrets.txt
{
  echo "KEYSTORE_BASE64=$(base64 < "$store" | tr -d '\n')"
  echo "KEYSTORE_PASSWORD=$(prop storePassword)"
  echo "KEY_ALIAS=$(prop keyAlias)"
  echo "KEY_PASSWORD=$(prop keyPassword)"
} > "$out"
chmod 600 "$out"
echo "Wrote $out (git-ignored). Add each line as a repository secret:"
echo "GitHub → Settings → Secrets and variables → Actions → New repository secret. Then delete $out."
