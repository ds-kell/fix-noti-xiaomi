#!/usr/bin/env bash
# CI only: rebuilds signing/hypernotifyfix-release.jks and keystore.properties from the
# KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD secrets.
# Usage: ci-signing-key.sh [--required]   (without --required, missing secrets = debug key)
set -euo pipefail
cd "$(dirname "$0")/.."

# Tolerate secrets pasted as whole "NAME=value" lines or with stray whitespace/newlines.
clean() { local v="${!1-}"; v="${v#"$1="}"; printf '%s' "$v" | tr -d '\r\n'; }
b64=$(clean KEYSTORE_BASE64 | tr -d ' \t'); store_pw=$(clean KEYSTORE_PASSWORD); alias=$(clean KEY_ALIAS); key_pw=$(clean KEY_PASSWORD)

if [ -z "$b64" ]; then
  if [ "${1-}" = "--required" ]; then echo "::error::Secret KEYSTORE_BASE64 is missing"; exit 1; fi
  echo "No signing secrets: using the debug key"; exit 0
fi
for name in KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
  [ -n "$(clean $name)" ] || { echo "::error::Secret $name is missing or empty"; exit 1; }
done

mkdir -p signing
if ! printf '%s' "$b64" | base64 -d > signing/hypernotifyfix-release.jks 2>/dev/null || [ ! -s signing/hypernotifyfix-release.jks ]; then
  echo "::error::Secret KEYSTORE_BASE64 is not valid base64 (paste only the text after 'KEYSTORE_BASE64=')"; exit 1
fi
if ! keytool -list -keystore signing/hypernotifyfix-release.jks -storepass "$store_pw" >/dev/null 2>&1; then
  echo "::error::KEYSTORE_PASSWORD does not open the keystore (or KEYSTORE_BASE64 is truncated)"; exit 1
fi
if ! keytool -list -keystore signing/hypernotifyfix-release.jks -storepass "$store_pw" -alias "$alias" >/dev/null 2>&1; then
  echo "::error::KEY_ALIAS '$alias' is not in the keystore"; exit 1
fi
printf 'storeFile=signing/hypernotifyfix-release.jks\nstorePassword=%s\nkeyAlias=%s\nkeyPassword=%s\n' \
  "$store_pw" "$alias" "$key_pw" > keystore.properties
keytool -list -v -keystore signing/hypernotifyfix-release.jks -storepass "$store_pw" -alias "$alias" | grep -m1 "SHA256:" || true
