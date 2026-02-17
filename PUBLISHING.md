# Publishing Guide

This document explains how to publish V8V to all distribution channels.

> **Architecture note (v0.3.0+):** V8V ships as a **single package** on every platform.
> The old `connector-mcp` and `connector-remote` modules were merged into `core`.
> There is only one artifact to publish per channel.

---

## Overview

| Channel | Who Consumes | Published Artifact |
|---------|-------------|-------------------|
| **Maven Central** | Android / Kotlin / JVM developers | `io.github.alimomin1998:core` (single artifact) |
| **npm** | Web / JS / TS developers | `v8v-core` (single package) |
| **GitHub Release + SPM** | iOS / macOS Swift developers | `V8VCore.xcframework.zip` |

---

## 1. Maven Central (Sonatype Central Portal)

### One-time setup (MANUAL)

1. **Create Sonatype account**: https://central.sonatype.com/
2. **Register namespace** `io.github.alimomin1998` at https://central.sonatype.com/publishing/namespaces
3. **Generate GPG key** (for signing artifacts):
   ```bash
   gpg --full-generate-key
   gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
   ```
4. **Add credentials to `~/.gradle/gradle.properties`** (NOT the project one):
   ```properties
   mavenCentralUsername=your_sonatype_user_token_username
   mavenCentralPassword=your_sonatype_user_token_password
   signingInMemoryKeyId=LAST8CHARS
   signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----...-----END PGP PRIVATE KEY BLOCK-----
   signingInMemoryKeyPassword=your_gpg_passphrase
   ```

### Publish

```bash
# Test locally first
./gradlew publishToMavenLocal

# Publish and auto-release to Maven Central
./gradlew publishAndReleaseToMavenCentral
```

Monitor deployments at: https://central.sonatype.com/publishing/deployments

---

## 2. npm (JavaScript / TypeScript)

### One-time setup (MANUAL)

1. **Create npm account**: https://www.npmjs.com/signup
2. **Login locally**:
   ```bash
   npm login
   ```

### Publish

```bash
# Build the JS distribution
./gradlew :core:jsBrowserProductionLibraryDistribution

# Copy package.json to dist
cp npm/package.json core/build/dist/js/productionLibrary/

# Publish
cd core/build/dist/js/productionLibrary
npm publish --access public
```

---

## 3. XCFramework + Swift Package Manager

### One-time setup (MANUAL)

1. **GitHub repo must be public** (SPM reads from the repo directly).
2. **GitHub CLI** (`gh`) installed for creating releases.

### Publish

```bash
# Build XCFramework
./gradlew assembleV8VCoreReleaseXCFramework

# Zip it
cd core/build/XCFrameworks/release
zip -r ../../../../V8VCore.xcframework.zip V8VCore.xcframework
cd ../../../..

# Compute checksum
swift package compute-checksum V8VCore.xcframework.zip

# Create GitHub Release and upload
gh release create v0.3.0 \
  --title "v0.3.0" \
  --notes "Release 0.3.0" \
  V8VCore.xcframework.zip

# Update Package.swift: uncomment the url/checksum block, paste values
```

---

## Automated Release

The `scripts/release.sh` script automates steps 1-3:

```bash
./scripts/release.sh 0.3.0
```

It will:
1. Bump version in `build.gradle.kts`
2. Run tests
3. Publish to Maven Central (if credentials set)
4. Build + publish npm (if logged in)
5. Build + zip XCFramework
6. Print git tag + GitHub Release commands

---

## CI Publishing (GitHub Actions)

To automate publishing on git tag push, add these secrets to your GitHub repo:

| Secret | Description |
|--------|-------------|
| `SONATYPE_USERNAME` | Sonatype token username |
| `SONATYPE_PASSWORD` | Sonatype token password |
| `GPG_KEY_ID` | Last 8 chars of GPG key fingerprint |
| `GPG_SIGNING_KEY` | ASCII-armored GPG private key |
| `GPG_PASSPHRASE` | GPG key passphrase |
| `NPM_TOKEN` | npm publish token |

The `publish.yml` workflow triggers on `v*` tags.
