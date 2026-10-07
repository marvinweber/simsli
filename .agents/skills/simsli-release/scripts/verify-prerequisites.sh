#!/usr/bin/env bash
set -euo pipefail

# Find repo root
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$REPO_ROOT"

echo "=== Simsli Release: Verifying Prerequisites ==="

# 1. Java Runtime Detection
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
    if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
        export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
        export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null)"
    fi
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    echo "✅ Java runtime: $JAVA_HOME"
else
    echo "❌ Java runtime not found! Please set JAVA_HOME or install JDK."
    exit 1
fi

# 2. SSH Agent / Git Signing Check
echo -n "Checking SSH signing key... "
if ssh-add -l >/dev/null 2>&1; then
    KEY_COUNT=$(ssh-add -l | wc -l | tr -d ' ')
    echo "✅ SSH agent has $KEY_COUNT active identity/identities loaded."
else
    echo "⚠️  SSH agent has no loaded identities! Git commit/tag signing will fail."
    echo "   Please run 'ssh-add' or unlock your SSH key agent before committing/tagging."
fi

# 3. Keystore Configuration Check
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

# 4. Current Version
echo -n "Current Version: "
MAJOR=$(grep -E '^versionMajor=' version.properties | cut -d= -f2 | tr -d '[:space:]')
MINOR=$(grep -E '^versionMinor=' version.properties | cut -d= -f2 | tr -d '[:space:]')
PATCH=$(grep -E '^versionPatch=' version.properties | cut -d= -f2 | tr -d '[:space:]')
CODE=$(grep -E '^versionCode=' version.properties | cut -d= -f2 | tr -d '[:space:]')
echo "$MAJOR.$MINOR.$PATCH (versionCode: $CODE)"

# 5. Git Status Check
DIRTY_COUNT=$(git status --porcelain | wc -l | tr -d ' ')
if [ "$DIRTY_COUNT" -eq 0 ]; then
    echo "✅ Git working directory is clean."
else
    echo "ℹ️  Git working tree has $DIRTY_COUNT pending change(s)."
fi

echo "=== Verification complete ==="
