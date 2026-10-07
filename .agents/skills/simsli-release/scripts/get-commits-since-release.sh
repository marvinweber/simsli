#!/usr/bin/env bash
set -euo pipefail

# Find repo root
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$REPO_ROOT"

# Find latest git tag
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")

if [ -z "$LATEST_TAG" ]; then
    echo "No tags found. Showing all commits on current branch:"
    RANGE="HEAD"
else
    echo "Latest release tag: $LATEST_TAG"
    RANGE="$LATEST_TAG..HEAD"
fi

TOTAL_COMMITS=$(git rev-list --count "$RANGE")
echo "Found $TOTAL_COMMITS commit(s) since ${LATEST_TAG:-beginning}:"
echo ""

echo "--- Features (feat) ---"
git log "$RANGE" --oneline --grep="^feat" || true
echo ""

echo "--- Bug Fixes (fix) ---"
git log "$RANGE" --oneline --grep="^fix" || true
echo ""

echo "--- UI & Improvements (refactor / perf / ui) ---"
git log "$RANGE" --oneline --grep="^\(refactor\|perf\|ui\)" || true
echo ""

echo "--- Other Commits ---"
git log "$RANGE" --oneline --grep="^\(feat\|fix\|refactor\|perf\|ui\)" --invert-grep || true
echo ""
