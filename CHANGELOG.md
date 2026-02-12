# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-02-09

### Added

#### Core
- `VoiceAgent` orchestrator with `start()`, `stop()`, `destroy()`, `updateConfig()`
- `SpeechRecognitionEngine` interface for pluggable platform STT adapters
- `IntentResolver` with wildcard `*` pattern matching and named `{slot}` extraction
- Fuzzy intent matching using Dice similarity coefficient
- `ActionRouter` with three scopes: `LOCAL`, `MCP`, `REMOTE`
- `ActionHandler` interface with `LocalActionHandler`
- `VoiceAgentConfig` with runtime settings (language, continuous mode, fuzzy threshold, partial results)
- `VoiceAgentError` sealed class for structured error handling (`PermissionDenied`, `EngineError`, `ActionFailed`)
- `PermissionHelper` interface with platform implementations
- Normalized `audioLevel` StateFlow (0.0–1.0) for mic volume indicators
- `SpeechEvent` sealed class: `PartialResult`, `FinalResult`, `Error`, `RmsChanged`, `ReadyForSpeech`, `EndOfSpeech`

#### Platform Engines
- **Android**: `AndroidSpeechEngine` using `android.speech.SpeechRecognizer`
- **iOS/macOS**: `AppleSpeechEngine` using `SFSpeechRecognizer` + `AVAudioEngine`
- **Web**: `WebSpeechEngine` using the Web Speech API
- **JVM**: Stub `JvmSpeechEngine` (bring your own)

#### Connectors
- `connector-mcp`: MCP client with JSON-RPC 2.0 over HTTP, `McpActionHandler`
- `connector-remote`: Webhook client, `WebhookActionHandler` for n8n/Zapier/Make

#### Distribution
- Kotlin Multiplatform targets: Android, JVM, JS (browser), iOS (arm64, simulatorArm64, x64), macOS (arm64, x64)
- `maven-publish` plugin on all library modules (group: `io.v8v`, version: `0.1.0`)
- XCFramework build for iOS + macOS (`./gradlew assembleV8VCoreXCFramework`)
- Swift Package Manager support via `Package.swift`
- npm package with TypeScript definitions via `binaries.library()` + `generateTypeScriptDefinitions()`
- `@JsExport` facade (`VoiceAgentJs`) with callback-based API for JavaScript consumers

#### Examples
- `example-android`: Jetpack Compose app demonstrating LOCAL, MCP, and REMOTE scopes with settings UI and audio level indicator
- `example-web`: Vanilla HTML + JS demo with all 3 scopes (LOCAL, MCP, REMOTE), fuzzy matching explanation, and configurable MCP/webhook URLs
- `example-jvm`: JVM CLI app with console-simulated speech input, all 3 scopes, and MCP/webhook integration
- `example-macos`: SwiftUI macOS app template with XCFramework integration guide
- `example-mcp-server`: Standalone Node.js MCP server with 4 tools (create_task, list_tasks, delete_task, search_tasks) for real cross-platform MCP testing
