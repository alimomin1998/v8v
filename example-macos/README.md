# macOS Example (SwiftUI)

A native macOS app demonstrating V8V with SwiftUI.

## Quick Start

1. **Build the XCFramework** (from project root):
   ```bash
   ./gradlew assembleV8VCoreReleaseXCFramework
   ```

2. **Open the Xcode project:**
   ```bash
   open example-macos/V8VMac.xcodeproj
   ```

3. **Hit Cmd+R** to build and run.

The app will request microphone and speech recognition permissions on first launch.

## Voice Commands

| Say | Action |
|-----|--------|
| "add project status update" | Adds "project status update" to todo list |
| "remove project status update" | Removes it from list |
| "list todos" | Shows all todos in log |

## Entitlements

The project includes `V8VMac.entitlements` with:
- `com.apple.security.app-sandbox` — required for App Store distribution
- `com.apple.security.device.audio-input` — microphone access

## Regenerating the Xcode Project

If you modify `project.yml`, regenerate with:
```bash
cd example-macos
xcodegen generate
```

Install xcodegen: `brew install xcodegen`
