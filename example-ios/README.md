# iOS Example (SwiftUI)

A native iOS app demonstrating V8V with SwiftUI.

## Quick Start

1. **Build the XCFramework** (from project root):
   ```bash
   ./gradlew assembleV8VCoreReleaseXCFramework
   ```

2. **Open the Xcode project:**
   ```bash
   open example-ios/V8VPhone.xcodeproj
   ```

3. **Select a simulator or device**, then hit **Cmd+R** to build and run.

> **Note:** Speech recognition requires a real device. The iOS Simulator
> does not support microphone input or `SFSpeechRecognizer`.

## Voice Commands

| Say | Action |
|-----|--------|
| "add milk" | Adds "milk" to todo list |
| "remove milk" | Removes "milk" from list |
| "list todos" | Shows all todos in log |

## Regenerating the Xcode Project

If you modify `project.yml`, regenerate with:
```bash
cd example-ios
xcodegen generate
```

Install xcodegen: `brew install xcodegen`
