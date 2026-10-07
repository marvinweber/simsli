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

echo "=== 1/3 Running Tests ==="
echo "Running server tests..."
(cd server && go test ./...)

echo "Running Android unit tests..."
"$REPO_ROOT/gradlew" testDebugUnitTest --quiet

echo ""
echo "=== 2/3 Building Release Artifacts ==="
echo "Building Phone Play Bundle (:app:bundlePlayRelease)..."
"$REPO_ROOT/gradlew" :app:bundlePlayRelease --quiet

echo "Building Wear OS Bundle (:wear:bundleRelease)..."
"$REPO_ROOT/gradlew" :wear:bundleRelease --quiet

echo "Building FOSS APK (:app:assembleFossRelease)..."
"$REPO_ROOT/gradlew" :app:assembleFossRelease --quiet

echo ""
echo "=== 3/3 Verifying Artifacts ==="

PHONE_AAB="app/build/outputs/bundle/playRelease/app-play-release.aab"
WEAR_AAB="wear/build/outputs/bundle/release/wear-release.aab"
FOSS_APK="app/build/outputs/apk/foss/release/app-foss-release.apk"

MISSING=0

verify_file() {
    local FILE="$1"
    local DESC="$2"
    if [ -f "$FILE" ]; then
        local SIZE
        SIZE=$(ls -lh "$FILE" | awk '{print $5}')
        local SHA
        if command -v sha256sum >/dev/null 2>&1; then
            SHA=$(sha256sum "$FILE" | awk '{print $1}')
        elif command -v shasum >/dev/null 2>&1; then
            SHA=$(shasum -a 256 "$FILE" | awk '{print $1}')
        else
            SHA="n/a"
        fi
        echo "✅ $DESC"
        echo "   Path:   $FILE"
        echo "   Size:   $SIZE"
        echo "   SHA256: $SHA"
    else
        echo "❌ $DESC missing: $FILE"
        MISSING=$((MISSING + 1))
    fi
}

verify_file "$PHONE_AAB" "Google Play Phone App Bundle"
verify_file "$WEAR_AAB" "Google Play Wear OS App Bundle"
verify_file "$FOSS_APK" "FOSS Release APK"

if [ "$MISSING" -gt 0 ]; then
    echo ""
    echo "❌ Build verification failed: $MISSING artifact(s) missing!"
    exit 1
fi

echo ""
echo "🎉 All release artifacts built and verified successfully!"
