// swift-tools-version:5.9
import PackageDescription

// ═══════════════════════════════════════════════════════════════════════
// V8V Core — Swift Package Manager manifest
//
// For CONSUMERS: add this repo as a package dependency in Xcode:
//   File → Add Package Dependencies → paste repo URL
//
// For LOCAL DEVELOPMENT: build the XCFramework first:
//   ./gradlew assembleV8VCoreXCFrameworkRelease
//
// The release script (scripts/release.sh) will:
//   1. Build the XCFramework
//   2. Zip it
//   3. Upload to GitHub Release
//   4. Print the checksum for you to paste here
// ═══════════════════════════════════════════════════════════════════════

let package = Package(
    name: "V8VCore",
    platforms: [
        .iOS(.v15),
        .macOS(.v13),
    ],
    products: [
        .library(
            name: "V8VCore",
            targets: ["V8VCore"]
        ),
    ],
    targets: [
        // ─────────────────────────────────────────────────────
        // For GitHub Release distribution, uncomment the url/checksum
        // block and comment out the path block:
        // ─────────────────────────────────────────────────────

        // .binaryTarget(
        //     name: "V8VCore",
        //     url: "https://github.com/AliHaider-codes/v8v/releases/download/v0.1.0/V8VCore.xcframework.zip",
        //     checksum: "CHECKSUM_FROM_RELEASE_SCRIPT"
        // ),

        // ─────────────────────────────────────────────────────
        // For local development (build XCFramework first):
        // ─────────────────────────────────────────────────────
        .binaryTarget(
            name: "V8VCore",
            path: "core/build/XCFrameworks/release/V8VCore.xcframework"
        ),
    ]
)
