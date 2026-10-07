#!/usr/bin/env bash
set -euo pipefail

# Find repo root
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$REPO_ROOT"

# Auto-resolve JAVA_HOME
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
    if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
        export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
        export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null)"
    fi
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

echo "=== Version Updated Successfully ==="
echo "Version:     $FULL_VERSION (versionCode: $CODE)"
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
