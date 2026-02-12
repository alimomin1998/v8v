# Contributing to V8V

Thank you for your interest in contributing!

## Getting Started

1. Fork the repository
2. Clone your fork
3. Open the project in Android Studio (or IntelliJ IDEA with Android plugin)
4. Make your changes in a feature branch
5. Run `./gradlew ktlintCheck` to verify formatting
6. Submit a pull request

## Project Structure

- `core/src/commonMain/` — Platform-agnostic framework code (VoiceAgent, IntentResolver, ActionRouter, VoiceAgentCallbacks)
- `core/src/androidMain/` — Android SpeechRecognizer engine
- `core/src/iosMain/` — iOS SFSpeechRecognizer + AVAudioSession engine
- `core/src/macosMain/` — macOS SFSpeechRecognizer engine (no AVAudioSession)
- `core/src/jsMain/` — Web Speech API engine + @JsExport facade
- `core/src/jvmMain/` — JVM platform stub
- `core/src/commonTest/` — Shared unit tests (IntentResolver, ActionRouter)
- `connector-mcp/` — MCP client (JSON-RPC 2.0 over HTTP)
- `connector-remote/` — Webhook client (n8n, Zapier, Make)
- `example-android/` — Android Compose demo (all 3 scopes)
- `example-ios/` — iOS SwiftUI demo (all 3 scopes)
- `example-macos/` — macOS SwiftUI demo (all 3 scopes)
- `example-web/` — Web demo (HTML + vanilla JS)
- `example-jvm/` — JVM CLI demo (simulated speech)
- `example-mcp-server/` — Node.js MCP server for testing

## Development Guidelines

- Write Kotlin following the [official style guide](https://kotlinlang.org/docs/coding-conventions.html)
- Run `./gradlew ktlintFormat` to auto-format before committing
- Add tests for new features (place them in `commonTest` when possible)
- Keep the public API minimal and well-documented
- Platform adapters must implement the `SpeechRecognitionEngine` interface
- Avoid adding external dependencies unless absolutely necessary

## Areas of Contribution

- **Windows Adapter**: Implement `SpeechRecognitionEngine` using Windows Speech API / SAPI
- **Linux Adapter**: Implement using Vosk, Whisper.cpp, or PipeWire/PulseAudio
- **AI/ML Intent Matching**: Embeddings-based or transformer-based intent resolution
- **MCP Transport**: Android Bound Services, Unix sockets, stdio
- **Connectors**: gRPC, MQTT, GraphQL subscriptions
- **Examples**: More demo apps showcasing different use cases
- **Documentation**: Guides, API docs, tutorials, video walkthroughs

## Running Tests

```bash
# All JVM tests (core + connectors)
./gradlew :core:jvmTest :connector-mcp:jvmTest :connector-remote:jvmTest

# JS browser tests
./gradlew :core:jsBrowserTest

# Lint check
./gradlew ktlintCheck
```

## Code of Conduct

Be respectful, constructive, and inclusive. We're here to build something useful together.

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
