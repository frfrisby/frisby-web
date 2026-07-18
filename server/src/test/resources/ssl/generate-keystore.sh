#!/usr/bin/env sh
# Regenerates the test.p12 PKCS12 keystore used by ServerSslTest.
#
# The keystore contains a single self-signed RSA-2048 certificate valid for
# 3650 days (~10 years).  It covers both DNS:localhost and IP:127.0.0.1 via a
# Subject Alternative Name extension — the JDK HttpClient validates SANs
# strictly and rejects certificates whose only CN matches the host name.
#
# Run this script from any directory; the output is always written to the
# same directory as the script itself.
#
# Requires: keytool (ships with any JDK 11+)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
KEYSTORE="$SCRIPT_DIR/test.p12"

keytool -genkeypair \
  -alias test \
  -keyalg RSA -keysize 2048 \
  -dname "CN=localhost" \
  -ext "san=dns:localhost,ip:127.0.0.1" \
  -validity 3650 \
  -storetype PKCS12 \
  -keystore "$KEYSTORE" \
  -storepass changeit -keypass changeit \
  -noprompt

echo "Keystore written to: $KEYSTORE"
keytool -list -v -keystore "$KEYSTORE" -storepass changeit

