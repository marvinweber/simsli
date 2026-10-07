#!/usr/bin/env bash
set -euo pipefail

# Find repo root
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$REPO_ROOT"

# Read version
MAJOR=$(grep -E '^versionMajor=' version.properties | cut -d= -f2 | tr -d '[:space:]')
MINOR=$(grep -E '^versionMinor=' version.properties | cut -d= -f2 | tr -d '[:space:]')
PATCH=$(grep -E '^versionPatch=' version.properties | cut -d= -f2 | tr -d '[:space:]')
VERSION="$MAJOR.$MINOR.$PATCH"
TAG="v$VERSION"
TITLE="Simsli $VERSION"
COMMIT_HASH=$(git rev-parse --short HEAD 2>/dev/null || echo "head")
ASSET_NAME="simsli-foss-${VERSION}-${COMMIT_HASH}.apk"
APK_FILE="app/build/outputs/apk/foss/release/app-foss-release.apk"

DRAFT_FLAG=""
if [[ "${1:-}" == "--draft" ]]; then
    DRAFT_FLAG="--draft"
fi

echo "=== Creating GitHub Release: $TITLE ($TAG) ==="

# Check APK
if [ ! -f "$APK_FILE" ]; then
    echo "⚠️ Warning: FOSS APK not found at $APK_FILE"
    echo "   Run build-release-artifacts.sh first if you wish to attach it."
fi

# Extract changelog section for this version
NOTES_TMP=$(mktemp)
trap 'rm -f "$NOTES_TMP"' EXIT

# Extract section from CHANGELOG.md matching ## [VERSION] up to next ## [
awk -v ver="$VERSION" '
    BEGIN { found = 0 }
    $0 ~ "^## \\[" ver "\\]" { found = 1; next }
    found && /^## \[/ { exit }
    found { print }
' CHANGELOG.md | sed -e '/./,$!d' > "$NOTES_TMP"

# If notes are empty, fallback to simple notice
if [ ! -s "$NOTES_TMP" ]; then
    echo "Release $TITLE" > "$NOTES_TMP"
fi

if ! command -v gh >/dev/null 2>&1; then
    echo "ℹ️  GitHub CLI ('gh') is not installed."
    echo "   Install via: brew install gh && gh auth login"
    echo ""
    echo "You can create the release manually at:"
    echo "👉 https://github.com/marvinweber/simsli/releases/new?tag=$TAG&title=$(echo "$TITLE" | tr ' ' '+')"
    echo ""
    echo "--- Suggested Release Notes ---"
    cat "$NOTES_TMP"
    echo "-------------------------------"
    exit 0
fi

# gh is available
echo "Publishing via gh release create..."
CMD=(gh release create "$TAG" --title "$TITLE" --notes-file "$NOTES_TMP")

if [ -f "$APK_FILE" ]; then
    CMD+=("$APK_FILE#$ASSET_NAME")
fi

if [ -n "$DRAFT_FLAG" ]; then
    CMD+=("$DRAFT_FLAG")
fi

echo "Running: ${CMD[*]}"
"${CMD[@]}"

echo "🎉 GitHub Release created successfully!"
