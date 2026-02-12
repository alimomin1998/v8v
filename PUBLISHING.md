# Publishing Guide

This document explains how to publish V8V to all distribution channels.

---

## Overview

| Channel | Who Consumes | Published Artifact |
|---------|-------------|-------------------|
| **Maven Central** | Android / Kotlin / JVM developers | `io.v8v:core`, `connector-mcp`, `connector-remote` |
| **npm** | Web / JS / TS developers | `v8v-core` |
| **GitHub Release + SPM** | iOS / macOS Swift developers | `V8VCore.xcframework.zip` |

---

## 1. Maven Central (Sonatype OSSRH)

### One-time setup (MANUAL)

1. **Create Sonatype account**: https://issues.sonatype.org/secure/Signup!default.jspa
2. **Create a new project ticket**: Request `io.v8v` group ID
   - Go to https://issues.sonatype.org
   - Create New Issue → Community Support → New Project
   - Group Id: `io.v8v`
   - Project URL: `https://github.com/alimomin1998/v8v`
   - SCM URL: `https://github.com/alimomin1998/v8v.git`
3. **Generate GPG key** (for signing artifacts):
   ```bash
   gpg --gen-key
   # Note the key ID (last 8 chars of the fingerprint)
   gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
   ```
4. **Add credentials to `~/.gradle/gradle.properties`** (NOT the project one):
   ```properties
   sonatype.username=your_sonatype_username
   sonatype.password=your_sonatype_password
   signing.keyId=LAST8CHARS
   signing.password=your_gpg_passphrase
   signing.secretKeyRingFile=/Users/you/.gnupg/secring.gpg
   ```

### Publish

```bash
# Test locally first
./gradlew publishToMavenLocal

# Publish to Maven Central staging
./gradlew publishAllPublicationsToMavenCentralRepository

# Then go to https://s01.oss.sonatype.org → Staging Repositories → Close → Release
```

After first successful release, subsequent releases auto-sync to Maven Central.

---

## 2. npm (JavaScript / TypeScript)

### One-time setup (MANUAL)

1. **Create npm account**: https://www.npmjs.com/signup
2. **Login locally**:
   ```bash
   npm login
   ```
3. **Update `npm/package.json`** with your npm scope/org if needed.

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
gh release create v0.1.0 \
  --title "v0.1.0" \
  --notes "Release 0.1.0" \
  V8VCore.xcframework.zip

# Update Package.swift: uncomment the url/checksum block, paste values
```

---

## Automated Release

The `scripts/release.sh` script automates steps 1-3:

```bash
./scripts/release.sh 0.2.0
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
| `SONATYPE_USERNAME` | Sonatype OSSRH username |
| `SONATYPE_PASSWORD` | Sonatype OSSRH password |
| `GPG_KEY_ID` | Last 8 chars of GPG key fingerprint |
| `GPG_PRIVATE_KEY` | Base64-encoded GPG private key |
| `GPG_PASSPHRASE` | GPG key passphrase |
| `NPM_TOKEN` | npm publish token |

A `publish.yml` workflow can be added later to trigger on `v*` tags.
