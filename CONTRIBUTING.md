# Contributing to V8V

Thank you for your interest in contributing!

## Getting Started

1. Fork the repository
2. Clone your fork
3. Open the project in Android Studio (or IntelliJ IDEA with Android plugin)
4. Make your changes in a feature branch
5. Submit a pull request

## Project Structure

- `core/src/commonMain/` — Platform-agnostic framework code (Kotlin Multiplatform)
- `core/src/androidMain/` — Android speech recognition adapter
- `core/src/jvmMain/` — JVM platform stub
- `core/src/commonTest/` — Shared unit tests
- `example-android/` — Demo Android app built with Jetpack Compose

## Development Guidelines

- Write Kotlin following the [official style guide](https://kotlinlang.org/docs/coding-conventions.html)
- Add tests for new features (place them in `commonTest` when possible)
- Keep the public API minimal and well-documented
- Platform adapters must implement the `SpeechRecognitionEngine` interface
- Avoid adding external dependencies unless absolutely necessary

## Areas of Contribution

- **iOS Adapter**: Implement `SpeechRecognitionEngine` using `SFSpeechRecognizer`
- **Web Adapter**: Implement using the Web Speech API
- **Intent Matching**: Fuzzy matching, ML-based resolution
- **Examples**: More demo apps showcasing different use cases
- **Documentation**: Guides, API docs, tutorials

## Code of Conduct

Be respectful, constructive, and inclusive. We're here to build something useful together.

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
