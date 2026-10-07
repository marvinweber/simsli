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
echo "$MAJOR.$MINOR.$PATCH (versionCode: $CODE)"

# 4. Git Working Tree Status
DIRTY_COUNT=$(git status --porcelain | wc -l | tr -d ' ')
if [ "$DIRTY_COUNT" -eq 0 ]; then
    echo "✅ Git working directory is clean."
else
    echo "ℹ️  Git working tree has $DIRTY_COUNT pending change(s)."
fi

echo "=== Verification complete ==="
