# macOS Example (SwiftUI)

A native macOS app demonstrating V8V with SwiftUI.

## Setup

1. **Build the XCFramework:**
   ```bash
   cd ..  # project root
   ./gradlew assembleV8VCoreXCFrameworkRelease
   ```

2. **Create Xcode project:**
   - Open Xcode → New Project → macOS → App
   - Set product name to "V8VMac"
   - Language: Swift, Interface: SwiftUI

3. **Add the framework:**
   - Drag `core/build/XCFrameworks/release/V8VCore.xcframework` into your project
   - Or use SPM: File → Add Package Dependencies → paste repo URL

4. **Add entitlements:**
   In your `.entitlements` file:
   ```xml
   <key>com.apple.security.device.audio-input</key>
   <true/>
   ```

5. **Add Info.plist keys:**
   ```xml
   <key>NSSpeechRecognitionUsageDescription</key>
   <string>V8V needs speech recognition to understand your commands.</string>
   <key>NSMicrophoneUsageDescription</key>
   <string>V8V needs microphone access to listen for voice commands.</string>
   ```

6. **Copy the Swift file:**
   Replace your `ContentView.swift` (and `App.swift`) with `VoiceAgentMac.swift`.

7. **Build and run!**

## Voice Commands

| Say | Action |
|-----|--------|
| "add milk" | Adds "milk" to todo list |
| "remove milk" | Removes "milk" from list |
| "list todos" | Shows all todos in log |

## Adding MCP & Remote

To add MCP and Remote connectors, build and add their XCFrameworks too:
- `connector-mcp` and `connector-remote` modules can also produce frameworks
- Import them and use `McpActionHandler` / `WebhookActionHandler` just like on Android

## Flow Collection in Swift

Kotlin Flows need an adapter to work in Swift. Options:
- [SKIE](https://skie.touchlab.co/) — Generates Swift-friendly async sequences
- [KMP-NativeCoroutines](https://github.com/nicklockwood/KMP-NativeCoroutines) — Combine/async-await wrappers
- Manual wrapper using `Kotlinx_coroutines_coreFlowCollector`
