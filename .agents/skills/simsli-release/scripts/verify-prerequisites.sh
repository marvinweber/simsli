#!/usr/bin/env bash
set -euo pipefail

# Find repo root
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$REPO_ROOT"

echo "=== Simsli Release: Verifying Prerequisites ==="

# 1. Java Runtime Check (Requires JAVA_HOME to be defined)
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
    echo "❌ JAVA_HOME is not defined or invalid!"
    echo "   Please define JAVA_HOME before running this script (e.g. export JAVA_HOME=...)."
    exit 1
fi
echo "✅ Java runtime: $JAVA_HOME"

# 2. Keystore Configuration Check
echo -n "Checking release signing configuration... "
KEYSTORE_FOUND=false
if [ -f "local.properties" ]; then
    KEYSTORE_PATH=$(grep -E '^release\.keystore\.file=' local.properties 2>/dev/null | cut -d= -f2- | tr -d '\r\n[:space:]' || true)
    if [ -n "$KEYSTORE_PATH" ] && [ -f "$KEYSTORE_PATH" ]; then
        echo "✅ Keystore found in local.properties ($KEYSTORE_PATH)"
        KEYSTORE_FOUND=true
    fi
fi

if [ "$KEYSTORE_FOUND" = false ] && [ -n "${RELEASE_KEYSTORE_FILE:-}" ] && [ -f "$RELEASE_KEYSTORE_FILE" ]; then
    echo "✅ Keystore found in env RELEASE_KEYSTORE_FILE ($RELEASE_KEYSTORE_FILE)"
    KEYSTORE_FOUND=true
fi

if [ "$KEYSTORE_FOUND" = false ]; then
    echo "⚠️  Release keystore not detected in local.properties or RELEASE_KEYSTORE_FILE."
    echo "   Release builds will fail unless signing credentials are provided."
fi

# 3. Current Version
echo -n "Current Version: "
MAJOR=$(grep -E '^versionMajor=' version.properties | cut -d= -f2 | tr -d '[:space:]')
MINOR=$(grep -E '^versionMinor=' version.properties | cut -d= -f2 | tr -d '[:space:]')
PATCH=$(grep -E '^versionPatch=' version.properties | cut -d= -f2 | tr -d '[:space:]')
CODE=$(grep -E '^versionCode=' version.properties | cut -d= -f2 | tr -d '[:space:]')
PHONE_CODE=$((10000000 + CODE))
WEAR_CODE=$((20000000 + CODE))
echo "$MAJOR.$MINOR.$PATCH (base: $CODE, phone: $PHONE_CODE, wear: $WEAR_CODE)"

# 4. Client/Server Compatibility Check
SERVER_CONFIG="server/internal/config/config.go"
if [ -f "$SERVER_CONFIG" ]; then
    MIN_APP_VER=$(grep -E 'MinAppVersion:\s+getEnv\("SIMSLI_MIN_APP_VERSION"' "$SERVER_CONFIG" | sed -E 's/.*"SIMSLI_MIN_APP_VERSION", "([^"]+)".*/\1/')
    echo "ℹ️  Server requires minimum app version: ${MIN_APP_VER:-unknown}"
fi

APP_AUTH_STORAGE="app/src/main/java/net/marvinweber/simsli/data/local/AuthTokenStorage.kt"
if [ -f "$APP_AUTH_STORAGE" ]; then
    MIN_API_VER=$(grep -E 'const val MIN_SERVER_API_VERSION' "$APP_AUTH_STORAGE" | awk '{print $NF}')
    echo "ℹ️  App requires minimum server API version: ${MIN_API_VER:-unknown}"
fi

# 5. Git Working Tree Status
DIRTY_COUNT=$(git status --porcelain | wc -l | tr -d ' ')
if [ "$DIRTY_COUNT" -eq 0 ]; then
    echo "✅ Git working directory is clean."
else
    echo "ℹ️  Git working tree has $DIRTY_COUNT pending change(s)."
fi

echo "=== Verification complete ==="
