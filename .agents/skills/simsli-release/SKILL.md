---
name: simsli-release
description: >-
  Use this skill when preparing, bumping, building, verifying, or publishing a new release of Simsli (Android app, Wear OS companion, and server).
---

# Simsli Release Runbook

This skill guides the deterministic release workflow for Simsli across all modules:
- Android Phone App (Google Play Bundle & FOSS APK)
- Wear OS Companion App (Google Play Bundle)
- Simsli Go Server & Docker Image

---

## Prerequisites & Architecture

- **Java Environment**: `JAVA_HOME` must be explicitly defined in your shell environment before executing scripts or Gradle commands (e.g., `export JAVA_HOME="/path/to/jdk"`).
- **Version Authority**: `version.properties` at repository root controls versioning across `:app` and `:wear`.
- **Server Version Sync**: Root Gradle tasks (`bumpPatch`, `bumpMinor`, `bumpMajor`) update `version.properties`, `server/Dockerfile` (`ARG VERSION=...`), and `server/internal/config/config.go` (`var Version = "..."`).
- **Signing**: Keystore settings live in `local.properties` (or env vars `RELEASE_KEYSTORE_*`). Git tags and commits **must** be signed (never use `--no-gpg-sign`).
- **Google Play Dual Bundles**: Phone and Wear OS require separate `.aab` artifacts under the same `applicationId` (`net.marvinweber.simsli`).
- **Docker CI**: Pushing a tag (`v*`) triggers `.github/workflows/docker-publish.yml` to build and publish the multi-arch server image to GHCR.

---

## Workflow Steps

### Step 1: Pre-flight Verification

Run the verification helper:
```bash
./.agents/skills/simsli-release/scripts/verify-prerequisites.sh
```
Check the output for:
- ✅ Java runtime configured (`JAVA_HOME`)
- ✅ Release keystore configured in `local.properties` or environment
- Review any pending uncommitted changes in git.

---

### Step 2: Change Analysis & User-Facing Changelog

1. Inspect commits since the last release tag:
   ```bash
   ./.agents/skills/simsli-release/scripts/get-commits-since-release.sh
   ```
2. **Propose Semver Version Bump**:
   - **`major`** (e.g. `0.3.3` -> `1.0.0`): Breaking database or protocol changes, fundamental architecture rewrites.
   - **`minor`** (e.g. `0.3.3` -> `0.4.0`): Major new features or capabilities (e.g. Wear OS companion app, new tabs/screens).
   - **`patch`** (e.g. `0.3.3` -> `0.3.4`): Bug fixes, UI polish, performance improvements.
3. **Draft a User-Facing Changelog**:
   - Write from the **user's perspective** (not git commit jargon or internal code refactors).
   - Group into clear sections:
     - 🚀 **What's New**: Exciting new capabilities and features.
     - ✨ **Improvements**: Polish, usability tweaks, UI enhancements.
     - 🐛 **Bug Fixes**: Stability, resolved crashes, sync fixes.
4. **Mandatory Confirmation**:
   - **Ask the user to review and confirm** both the proposed bump (`patch`, `minor`, `major`) and the draft changelog text before executing any changes.

---

### Step 3: Bump Version & Update Changelog

1. Execute the confirmed bump type:
   ```bash
   ./.agents/skills/simsli-release/scripts/bump-version.sh <patch|minor|major>
   ```
   This invokes Gradle to atomically update:
   - `version.properties` (`versionCode` + 1, `versionMajor.Minor.Patch`)
   - `server/Dockerfile`
   - `server/internal/config/config.go`

2. Update `CHANGELOG.md`:
   - Prepend the approved user release notes under `## [X.Y.Z] - YYYY-MM-DD`.

3. Update `docs/FEATURE-SPEC.md` if any completed features now ship with this release.

---

### Step 4: Build & Verify Release Artifacts

Run the build helper:
```bash
./.agents/skills/simsli-release/scripts/build-release-artifacts.sh
```
This script:
1. Runs server unit tests (`go test ./...` in `server/`)
2. Runs Android unit tests (`testDebugUnitTest`)
3. Builds all three distribution artifacts:
   - Phone Play Bundle: `app/build/outputs/bundle/playRelease/app-play-release.aab`
   - Wear OS Play Bundle: `wear/build/outputs/bundle/release/wear-release.aab`
   - FOSS APK: `app/build/outputs/apk/foss/release/app-foss-release.apk`
4. Verifies their existence and prints file sizes and SHA256 checksums.

---

### Step 5: Git Commit & Signed Tag

1. Review changes:
   ```bash
   git diff version.properties server/ CHANGELOG.md docs/
   ```
2. Commit with signed git settings:
   ```bash
   git add version.properties server/Dockerfile server/internal/config/config.go CHANGELOG.md docs/FEATURE-SPEC.md
   git commit -m "chore(release): bump version to X.Y.Z (versionCode N)"
   ```
   *(If commit fails because the SSH agent is locked, stop immediately and ask the user to unlock it.)*
3. Create an annotated SSH-signed tag:
   ```bash
   git tag -s vX.Y.Z -m "Release vX.Y.Z: <Release Title / Summary>"
   ```

---

### Step 6: Publishing & Distribution
 
 > [!IMPORTANT]
 > **Do NOT push to remote automatically.** Never run `git push` on your own. Confirm with the user or provide the push commands for them to execute when ready.

 1. **Push to Remote (User Confirmation Required)**:
    ```bash
    git push origin main
    git push origin vX.Y.Z
    ```
    *(Pushing the tag triggers the Docker image build & publish workflow on GitHub Actions.)*

2. **Google Play Console**:
   - Open Play Console → Simsli.
   - **Phone track**: Create new release, upload `app-play-release.aab`.
   - **Wear OS track**: Upload `wear-release.aab` under the Wear OS release track.
   - Paste the user changelog into the release notes.

3. **GitHub Releases**:
   - Create a new release for tag `vX.Y.Z`.
   - Title: `Simsli vX.Y.Z`
   - Body: Paste the user changelog from `CHANGELOG.md`.
   - Attach binary asset: `app/build/outputs/apk/foss/release/app-foss-release.apk`.
