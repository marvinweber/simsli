#!/usr/bin/env bash
set -euo pipefail

# Find repo root
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$REPO_ROOT"

# Require JAVA_HOME to be defined
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
    echo "❌ JAVA_HOME is not defined or invalid!"
    echo "   Please define JAVA_HOME before running this script (e.g. export JAVA_HOME=...)."
    exit 1
fi

TYPE="${1:-patch}"

case "$TYPE" in
    patch)
        TASK="bumpPatch"
        ;;
    minor)
        TASK="bumpMinor"
        ;;
    major)
        TASK="bumpMajor"
        ;;
    code)
        TASK="bumpVersionCode"
        ;;
    *)
        echo "❌ Invalid bump type: $TYPE"
        echo "Usage: $0 [patch|minor|major|code]"
        exit 1
        ;;
esac

echo "Running Gradle task: $TASK..."
"$REPO_ROOT/gradlew" "$TASK" --quiet

# Verify updated version
MAJOR=$(grep -E '^versionMajor=' version.properties | cut -d= -f2 | tr -d '[:space:]')
MINOR=$(grep -E '^versionMinor=' version.properties | cut -d= -f2 | tr -d '[:space:]')
PATCH=$(grep -E '^versionPatch=' version.properties | cut -d= -f2 | tr -d '[:space:]')
CODE=$(grep -E '^versionCode=' version.properties | cut -d= -f2 | tr -d '[:space:]')
FULL_VERSION="$MAJOR.$MINOR.$PATCH"

PHONE_CODE=$((10000000 + CODE))
WEAR_CODE=$((20000000 + CODE))

echo "=== Version Updated Successfully ==="
echo "Version:     $FULL_VERSION (base: $CODE, phone: $PHONE_CODE, wear: $WEAR_CODE)"
echo "App/Wear:    version.properties updated"

# Verify server files
SERVER_CONFIG="server/internal/config/config.go"
SERVER_DOCKER="server/Dockerfile"

if grep -q "var Version = \"$FULL_VERSION\"" "$SERVER_CONFIG"; then
    echo "Server Go:   $SERVER_CONFIG verified ($FULL_VERSION)"
else
    echo "⚠️ Warning: $SERVER_CONFIG did not contain var Version = \"$FULL_VERSION\""
fi

if grep -q "ARG VERSION=$FULL_VERSION" "$SERVER_DOCKER"; then
    echo "Server Doc:  $SERVER_DOCKER verified ($FULL_VERSION)"
else
    echo "⚠️ Warning: $SERVER_DOCKER did not contain ARG VERSION=$FULL_VERSION"
fi
