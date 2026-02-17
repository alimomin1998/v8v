# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - 2026-02-16

### Changed
- **Single package architecture**: Merged `connector-mcp` and `connector-remote` into `core`. All platforms now get LOCAL + MCP + REMOTE support from one dependency.
- `VoiceAgentJs` (Web) now exposes `registerMcpAction()` and `registerWebhookAction()` methods.
- `AndroidSpeechEngine` now dispatches all `SpeechRecognizer` calls to the main thread via `Handler(Looper.getMainLooper())`, fixing crashes when `VoiceAgent` runs on `Dispatchers.Default`.
- `WebSpeechEngine` reuses the `SpeechRecognition` instance across start/stop cycles to avoid repeated permission prompts in Chrome.
- `VoiceAgent.start()` now calls `requestMicrophonePermission()` whenever status is not `GRANTED` (previously only on `NOT_DETERMINED`).
- `McpServerConfig` now supports `fromUrl()` factory for URL-based construction.

### Removed
- `connector-mcp` module (merged into `core`)
- `connector-remote` module (merged into `core`)
- `example-jvm` (JVM CLI example)
- Old `example-android` (replaced with new example using published artifacts)

### Added
- `example-android` — uses published `io.github.alimomin1998:core-android:0.3.0` from Maven Central
- `example-web` — uses published `v8v-core@0.3.0` from npm, all 3 action scopes
- `VoiceAgentJs.registerMcpAction()` — register MCP actions from JavaScript
- `VoiceAgentJs.registerWebhookAction()` — register webhook actions from JavaScript
- `McpServerConfig.fromUrl()` — create MCP config from a full URL string

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
- `VoiceAgentError` sealed class for structured error handling
- `PermissionHelper` interface with platform implementations
- Normalized `audioLevel` StateFlow (0.0-1.0) for mic volume indicators

#### Platform Engines
- **Android**: `AndroidSpeechEngine` using `android.speech.SpeechRecognizer`
- **iOS/macOS**: `AppleSpeechEngine` using `SFSpeechRecognizer` + `AVAudioEngine`
- **Web**: `WebSpeechEngine` using the Web Speech API
- **JVM**: Stub `JvmSpeechEngine` (bring your own)

#### Connectors (later merged into core in 0.3.0)
- MCP client with JSON-RPC 2.0 over HTTP, `McpActionHandler`
- Webhook client, `WebhookActionHandler` for n8n/Zapier/Make

#### Distribution
- Kotlin Multiplatform targets: Android, JVM, JS (browser), iOS, macOS
- Maven Central publishing, npm package, XCFramework, Swift Package Manager
- `@JsExport` facade (`VoiceAgentJs`) with callback-based API for JavaScript

#### Examples
- `example-android`: Jetpack Compose app with LOCAL, MCP, and REMOTE scopes
- `example-ios`: SwiftUI iOS app with all 3 scopes
- `example-macos`: SwiftUI macOS app with all 3 scopes
- `example-web`: HTML + JS demo with all 3 scopes
- `example-mcp-server`: Standalone Node.js MCP server for testing
