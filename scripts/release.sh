#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# V8V — Release Playbook
#
# Usage:  ./scripts/release.sh 0.2.0
#
# This script automates everything that CAN be automated.
# Manual steps (accounts, tokens) are listed in MANUAL_STEPS.md.
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

VERSION="${1:?Usage: $0 <version>  e.g. $0 0.2.0}"
TAG="v${VERSION}"
XCFRAMEWORK_ZIP="V8VCore.xcframework.zip"

echo "══════════════════════════════════════════════════════════"
echo "  V8V Release ${TAG}"
echo "══════════════════════════════════════════════════════════"

# ───────────────────────────────────────────────────
# Step 1: Bump version in root build.gradle.kts
# ───────────────────────────────────────────────────
echo ""
echo "▸ Step 1: Bump version to ${VERSION}"
sed -i.bak "s/version = \".*\"/version = \"${VERSION}\"/" build.gradle.kts
rm -f build.gradle.kts.bak
echo "  ✓ build.gradle.kts version set to ${VERSION}"

# ───────────────────────────────────────────────────
# Step 2: Run tests
# ───────────────────────────────────────────────────
echo ""
echo "▸ Step 2: Running tests..."
./gradlew :core:jvmTest :connector-mcp:jvmTest :connector-remote:jvmTest
echo "  ✓ All JVM tests passed"

# ───────────────────────────────────────────────────
# Step 3: Publish to Maven Local (verification)
# ───────────────────────────────────────────────────
echo ""
echo "▸ Step 3: Publishing to Maven Local (dry run)..."
./gradlew publishToMavenLocal
echo "  ✓ Maven Local publish succeeded"

# ───────────────────────────────────────────────────
# Step 4: Publish to Maven Central (Sonatype)
# ───────────────────────────────────────────────────
echo ""
echo "▸ Step 4: Publishing to Maven Central..."
echo "  Requires SONATYPE_USERNAME and SONATYPE_PASSWORD env vars."
echo "  Requires GPG signing key in gradle.properties."
if [ -n "${SONATYPE_USERNAME:-}" ]; then
    ./gradlew publishAllPublicationsToMavenCentralRepository
    echo "  ✓ Maven Central publish succeeded"
else
    echo "  ⚠ SONATYPE_USERNAME not set — skipping Maven Central publish."
    echo "    Run manually: ./gradlew publishAllPublicationsToMavenCentralRepository"
fi

# ───────────────────────────────────────────────────
# Step 5: Build JS/TS distribution
# ───────────────────────────────────────────────────
echo ""
echo "▸ Step 5: Building JS/TS distribution..."
./gradlew :core:jsBrowserProductionLibraryDistribution
echo "  ✓ JS output at core/build/dist/js/productionLibrary/"

# ───────────────────────────────────────────────────
# Step 6: Publish npm package
# ───────────────────────────────────────────────────
echo ""
echo "▸ Step 6: Publishing npm package..."
NPM_DIR="core/build/dist/js/productionLibrary"
# Copy the npm package.json into the dist folder
cp npm/package.json "${NPM_DIR}/package.json"
# Update version in npm package.json
sed -i.bak "s/\"version\": \".*\"/\"version\": \"${VERSION}\"/" "${NPM_DIR}/package.json"
rm -f "${NPM_DIR}/package.json.bak"

if command -v npm &> /dev/null && npm whoami &> /dev/null 2>&1; then
    cd "${NPM_DIR}"
    npm publish --access public
    cd - > /dev/null
    echo "  ✓ npm package published"
else
    echo "  ⚠ Not logged in to npm — skipping npm publish."
    echo "    Run manually: cd ${NPM_DIR} && npm publish --access public"
fi

# ───────────────────────────────────────────────────
# Step 7: Build XCFramework
# ───────────────────────────────────────────────────
echo ""
echo "▸ Step 7: Building XCFramework..."
./gradlew assembleV8VCoreXCFrameworkRelease
echo "  ✓ XCFramework built"

# ───────────────────────────────────────────────────
# Step 8: Zip XCFramework + compute checksum
# ───────────────────────────────────────────────────
echo ""
echo "▸ Step 8: Packaging XCFramework..."
XCFRAMEWORK_PATH="core/build/XCFrameworks/release/V8VCore.xcframework"
if [ -d "${XCFRAMEWORK_PATH}" ]; then
    cd core/build/XCFrameworks/release
    zip -r -q "../../../../${XCFRAMEWORK_ZIP}" V8VCore.xcframework
    cd - > /dev/null
    echo "  ✓ ${XCFRAMEWORK_ZIP} created"

    if command -v swift &> /dev/null; then
        CHECKSUM=$(swift package compute-checksum "${XCFRAMEWORK_ZIP}")
        echo "  ✓ Checksum: ${CHECKSUM}"
        echo ""
        echo "  ┌────────────────────────────────────────────────────┐"
        echo "  │ Update Package.swift with:                         │"
        echo "  │   url: \".../${TAG}/${XCFRAMEWORK_ZIP}\"             │"
        echo "  │   checksum: \"${CHECKSUM}\"                         │"
        echo "  └────────────────────────────────────────────────────┘"
    else
        echo "  ⚠ swift not found — compute checksum manually:"
        echo "    swift package compute-checksum ${XCFRAMEWORK_ZIP}"
    fi
else
    echo "  ⚠ XCFramework not found at ${XCFRAMEWORK_PATH}"
fi

# ───────────────────────────────────────────────────
# Step 9: Git tag
# ───────────────────────────────────────────────────
echo ""
echo "▸ Step 9: Git tag"
echo "  Run these commands manually after verifying everything:"
echo ""
echo "    git add -A"
echo "    git commit -m \"Release ${TAG}\""
echo "    git tag ${TAG}"
echo "    git push origin main --tags"
echo ""

# ───────────────────────────────────────────────────
# Step 10: GitHub Release
# ───────────────────────────────────────────────────
echo ""
echo "▸ Step 10: Create GitHub Release"
echo "  Run this after pushing the tag:"
echo ""
echo "    gh release create ${TAG} \\"
echo "      --title \"${TAG}\" \\"
echo "      --notes \"Release ${VERSION}\" \\"
echo "      ${XCFRAMEWORK_ZIP}"
echo ""

echo "══════════════════════════════════════════════════════════"
echo "  V8V Release ${TAG} build complete!"
echo "══════════════════════════════════════════════════════════"
