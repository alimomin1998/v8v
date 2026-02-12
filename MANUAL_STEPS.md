# Complete Step-by-Step Release Setup

Everything below is in exact order. Do Step 1 first, then Step 2, etc.
Each step tells you exactly what to type, what to click, and what to copy.

---

## STEP 1: Create GitHub Repository and Push Code

You need a public GitHub repo. SPM (Swift Package Manager) requires it to be public.

### 1.1 Create the repo

1. Open https://github.com/new in your browser
2. Fill in:
   - Repository name: `v8v`
   - Description: `Cross-platform voice orchestration framework`
   - Visibility: **Public**
   - Do NOT initialize with README (you already have one)
3. Click **Create repository**
4. You will see a page with push instructions. Copy the repo URL, it looks like:
   `https://github.com/AliHaider-codes/v8v.git`

### 1.2 Push your code

Open terminal. Run these commands one by one:

```bash
cd "/Users/ali/V8V voice agent"

git init

git add -A

git commit -m "Initial commit: V8V v0.1.0"

git branch -M main

git remote add origin https://github.com/AliHaider-codes/v8v.git

git push -u origin main
```

If it asks for credentials, enter your GitHub username and a Personal Access Token
(not your password). To create a token:
1. Go to https://github.com/settings/tokens
2. Click "Generate new token (classic)"
3. Select scopes: `repo`, `write:packages`
4. Click Generate
5. Copy the token and use it as your password

### 1.3 Install GitHub CLI (needed later for releases)

```bash
brew install gh
```

Then authenticate:

```bash
gh auth login
```

It will ask:
- Where do you use GitHub? → `GitHub.com`
- Protocol? → `HTTPS`
- Authenticate? → `Login with a web browser`
- Follow the browser prompt to complete login

**Done. Your code is now on GitHub.**

---

## STEP 2: Create Sonatype Account (for Maven Central)

This lets Android/Kotlin/JVM developers install your library with Gradle.

### 2.1 Sign up

1. Open https://central.sonatype.com/ in your browser
2. Click **Sign In** (top right)
3. Click **Sign up** or use **Sign in with GitHub**
4. Complete registration

### 2.2 Register your namespace

1. After logging in, go to https://central.sonatype.com/publishing/namespaces
2. Click **Add Namespace**
3. Enter namespace: `io.v8v`
4. It will ask you to verify ownership. Choose **GitHub verification**:
   - It will ask you to create a specific repo in your GitHub account to prove ownership
   - Follow the exact instructions shown (usually: create a temporary repo with a specific name)
   - After creating the repo, click **Verify**
5. Wait for approval (can be instant with GitHub verification, or up to 2 days)

**Note your Sonatype username and generate a User Token:**
1. Go to https://central.sonatype.com/account
2. Click **Generate User Token**
3. You will get a `username` and `password` — **save these**, you need them in Step 4

**Done. You can now publish to Maven Central.**

---

## STEP 3: Create GPG Signing Key

Maven Central requires all published files to be signed with GPG.

### 3.1 Install GPG (if not installed)

```bash
brew install gnupg
```

### 3.2 Generate a key

```bash
gpg --full-generate-key
```

It will ask:
- Kind of key: press `1` (RSA and RSA)
- Key size: type `3072`, press Enter
- Expiration: type `0` (does not expire), press Enter
- Confirm: type `y`, press Enter
- Real name: type your name (e.g. `Ali Haider`), press Enter
- Email: type your email, press Enter
- Comment: press Enter (leave empty)
- Confirm: type `O` (for Okay), press Enter
- It will ask for a passphrase — **type a password and remember it**

### 3.3 Find your key ID

```bash
gpg --list-keys --keyid-format short
```

You will see output like:

```
pub   rsa3072/ABC12345 2026-02-09 [SC]
      1234567890ABCDEF1234567890ABCDEF12345678
uid           [ultimate] Ali Haider <ali@example.com>
```

Your key ID is the 8 characters after `rsa3072/` — in this example: `ABC12345`

**Write this down. You need it below.**

### 3.4 Upload key to public server

Replace `ABC12345` with YOUR key ID:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys ABC12345
```

### 3.5 Export secret key ring

Replace `ABC12345` with YOUR key ID:

```bash
gpg --export-secret-keys ABC12345 > ~/.gnupg/secring.gpg
```

**Done. Your GPG key is ready.**

---

## STEP 4: Save Credentials in Gradle Properties

This file stores your Sonatype + GPG credentials so Gradle can use them.
This goes in YOUR HOME directory, NOT in the project.

### 4.1 Open or create the file

```bash
nano ~/.gradle/gradle.properties
```

If `nano` is unfamiliar, you can also use:
```bash
open -e ~/.gradle/gradle.properties
```

If the file doesn't exist, create the directory first:
```bash
mkdir -p ~/.gradle
touch ~/.gradle/gradle.properties
open -e ~/.gradle/gradle.properties
```

### 4.2 Add these lines

Replace every placeholder with your actual values:

```properties
sonatype.username=YOUR_SONATYPE_TOKEN_USERNAME
sonatype.password=YOUR_SONATYPE_TOKEN_PASSWORD
signing.keyId=ABC12345
signing.password=YOUR_GPG_PASSPHRASE
signing.secretKeyRingFile=/Users/ali/.gnupg/secring.gpg
```

Where:
- `sonatype.username` = the Token Username from Step 2.2
- `sonatype.password` = the Token Password from Step 2.2
- `signing.keyId` = the 8-char key ID from Step 3.3
- `signing.password` = the passphrase you typed in Step 3.2
- `signing.secretKeyRingFile` = `/Users/ali/.gnupg/secring.gpg`

Save and close the file.

**Done. Gradle can now sign and publish.**

---

## STEP 5: Create npm Account (for Web/JS developers)

This lets web developers install your library with `npm install v8v-core`.

### 5.1 Sign up

1. Open https://www.npmjs.com/signup in your browser
2. Fill in username, email, password
3. Click Create Account
4. Verify your email (check inbox, click link)

### 5.2 Login from terminal

```bash
npm login
```

It will open a browser for authentication. Complete the login.

To verify it worked:
```bash
npm whoami
```

It should print your npm username.

**Done. You can now publish npm packages.**

---

## STEP 6: Test Everything Locally (before first release)

Run these commands from the project root to make sure everything builds:

```bash
cd "/Users/ali/V8V voice agent"
```

### 6.1 Run tests

```bash
./gradlew :core:jvmTest :connector-mcp:jvmTest :connector-remote:jvmTest
```

Wait for `BUILD SUCCESSFUL`. If any test fails, fix it before proceeding.

### 6.2 Test Maven Local publish

```bash
./gradlew publishToMavenLocal
```

Wait for `BUILD SUCCESSFUL`. This publishes to your local machine only (safe, no internet).

### 6.3 Test JS build

```bash
./gradlew :core:jsBrowserProductionLibraryDistribution
```

Wait for `BUILD SUCCESSFUL`.

### 6.4 Test XCFramework build

```bash
./gradlew assembleV8VCoreXCFrameworkRelease
```

Wait for `BUILD SUCCESSFUL`.

**Done. Everything builds correctly.**

---

## STEP 7: First Release

Now do the actual release. This publishes to Maven Central, npm, and GitHub.

### 7.1 Run the release script

```bash
cd "/Users/ali/V8V voice agent"

./scripts/release.sh 0.1.0
```

The script will:
- Bump version to 0.1.0
- Run tests
- Publish to Maven Central (if credentials are set)
- Build JS distribution
- Publish to npm (if logged in)
- Build and zip XCFramework
- Print the checksum

### 7.2 Commit and tag

After the script finishes, run:

```bash
git add -A

git commit -m "Release v0.1.0"

git tag v0.1.0

git push origin main --tags
```

### 7.3 Create GitHub Release (for XCFramework / SPM)

```bash
gh release create v0.1.0 \
  --title "v0.1.0" \
  --notes "First release of V8V framework" \
  V8VCore.xcframework.zip
```

This uploads the XCFramework zip to GitHub so iOS/macOS developers can download it.

### 7.4 Update Package.swift with checksum

The release script printed a checksum. It looks like a long string:
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`

Open `Package.swift` in the project root. Find this section:

```swift
// .binaryTarget(
//     name: "V8VCore",
//     url: "https://github.com/AliHaider-codes/v8v/releases/download/v0.1.0/V8VCore.xcframework.zip",
//     checksum: "CHECKSUM_FROM_RELEASE_SCRIPT"
// ),
```

Change it to (uncomment and fill in your checksum):

```swift
.binaryTarget(
    name: "V8VCore",
    url: "https://github.com/AliHaider-codes/v8v/releases/download/v0.1.0/V8VCore.xcframework.zip",
    checksum: "PASTE_YOUR_ACTUAL_CHECKSUM_HERE"
),
```

And comment out the local path block below it:

```swift
// .binaryTarget(
//     name: "V8VCore",
//     path: "core/build/XCFrameworks/release/V8VCore.xcframework"
// ),
```

Then push:

```bash
git add Package.swift
git commit -m "Update Package.swift with release checksum"
git push origin main
```

### 7.5 Release Maven Central staging (if using Sonatype OSSRH)

If you used the classic Sonatype OSSRH portal:
1. Go to https://s01.oss.sonatype.org
2. Login with your Sonatype credentials
3. Click **Staging Repositories** (left sidebar)
4. Find your repository (it has your group name `iovoiceagent-XXXX`)
5. Select it, click **Close** (top bar)
6. Wait for validation checks to pass (takes ~2 minutes)
7. Click **Release**

If you used the new Central Portal (central.sonatype.com), publishing may be automatic.

---

## STEP 8: Verify Everything is Published

### Maven Central

Open: https://central.sonatype.com/search?q=io.v8v

You should see:
- `io.v8v:core`
- `io.v8v:connector-mcp`
- `io.v8v:connector-remote`

Note: Maven Central indexing can take 15-30 minutes after release.

### npm

Open: https://www.npmjs.com/package/v8v-core

You should see version 0.1.0 published.

### Swift Package Manager

1. Open Xcode
2. File → Add Package Dependencies
3. Paste: `https://github.com/AliHaider-codes/v8v`
4. It should find the package and show version 0.1.0

---

## STEP 9: Add GitHub Secrets for CI (Optional but Recommended)

This allows GitHub Actions to auto-run tests on every push.

1. Open your repo on GitHub
2. Click **Settings** tab (top bar)
3. Left sidebar: **Secrets and variables** → **Actions**
4. Click **New repository secret** for each:

| Click "New repository secret" | Name field | Secret field |
|------|------|------|
| Secret 1 | `SONATYPE_USERNAME` | Your Sonatype Token Username from Step 2.2 |
| Secret 2 | `SONATYPE_PASSWORD` | Your Sonatype Token Password from Step 2.2 |
| Secret 3 | `GPG_KEY_ID` | Your 8-char key ID from Step 3.3 |
| Secret 4 | `GPG_PASSPHRASE` | Your GPG passphrase from Step 3.2 |
| Secret 5 | `NPM_TOKEN` | Get from https://www.npmjs.com → click avatar → Access Tokens → Generate New Token → Classic Token → Automation → Generate → copy the token |

After adding these, the CI workflow at `.github/workflows/ci.yml` will run
automatically on every push to `main` and on every pull request.

---

## Future Releases

After the one-time setup above, every future release is just:

```bash
cd "/Users/ali/V8V voice agent"

# Pick your new version number
./scripts/release.sh 0.2.0

# Then commit, tag, push
git add -A
git commit -m "Release v0.2.0"
git tag v0.2.0
git push origin main --tags

# Create GitHub release
gh release create v0.2.0 \
  --title "v0.2.0" \
  --notes "What changed in this version" \
  V8VCore.xcframework.zip

# Update Package.swift checksum (printed by release script)
# Commit and push Package.swift
```

That's it. ~5 minutes per release.

---

## Quick Reference: What Goes Where

| What | Where It's Published | How Developers Install |
|------|---------------------|----------------------|
| `core`, `connector-mcp`, `connector-remote` | Maven Central | `implementation("io.v8v:core-android:0.1.0")` |
| JS/TS library | npm | `npm install v8v-core` |
| XCFramework | GitHub Release | Xcode → Add Package → paste repo URL |
