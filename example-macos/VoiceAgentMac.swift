/**
 * V8V — macOS SwiftUI Example
 *
 * Demonstrates using the V8VCore XCFramework from a native
 * macOS app with SwiftUI. Supports all three action scopes.
 *
 * Prerequisites:
 *   1. Build the XCFramework:
 *      ./gradlew assembleV8VCoreXCFrameworkRelease
 *
 *   2. Open this project in Xcode:
 *      - Create a new macOS App project
 *      - Add the XCFramework: File → Add Package Dependencies
 *        (or drag V8VCore.xcframework into the project)
 *      - Copy this file as ContentView.swift
 *
 *   3. Add entitlements:
 *      - com.apple.security.device.audio-input (Microphone)
 *
 *   4. Add to Info.plist:
 *      - NSSpeechRecognitionUsageDescription
 *      - NSMicrophoneUsageDescription
 *
 * Usage:
 *   This file is a complete SwiftUI app. Copy it into your Xcode project.
 */

import SwiftUI
import V8VCore
// import ConnectorMcp    // Uncomment if you add the MCP connector framework
// import ConnectorRemote // Uncomment if you add the Remote connector framework

// MARK: - App Entry Point

@main
struct V8VMacApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .frame(minWidth: 500, minHeight: 600)
        }
    }
}

// MARK: - View Model

@MainActor
class VoiceAgentViewModel: ObservableObject {
    @Published var isListening = false
    @Published var transcript = ""
    @Published var todos: [String] = []
    @Published var logs: [String] = []
    @Published var errorMessage = ""
    @Published var language = "en"

    private var agent: VoiceAgent?

    init() {
        setupAgent()
    }

    private func setupAgent() {
        let engine = AppleSpeechEngine()
        let config = VoiceAgentConfig(language: language)
        let permissionHelper = ApplePermissionHelper()

        agent = VoiceAgent(
            engine: engine,
            config: config,
            permissionHelper: permissionHelper
        )

        // LOCAL: "add <item>" → in-app todo list
        agent?.registerAction(
            intent: "todo.add",
            phrases: [
                "en": ["add *", "add * to todo", "add * to my list"],
                "es": ["agregar *", "agregar * a la lista"],
            ]
        ) { [weak self] resolved in
            DispatchQueue.main.async {
                self?.todos.append(resolved.extractedText)
                self?.addLog("[LOCAL] Added: \(resolved.extractedText)")
            }
        }

        // LOCAL: "remove <item>"
        agent?.registerAction(
            intent: "todo.remove",
            phrases: ["en": ["remove *", "delete *"]]
        ) { [weak self] resolved in
            DispatchQueue.main.async {
                guard let self = self else { return }
                if let idx = self.todos.firstIndex(where: {
                    $0.lowercased().contains(resolved.extractedText.lowercased())
                }) {
                    let removed = self.todos.remove(at: idx)
                    self.addLog("[LOCAL] Removed: \(removed)")
                }
            }
        }

        // LOCAL: "list todos"
        agent?.registerAction(
            intent: "todo.list",
            phrases: ["en": ["list todos", "show todos", "show my list"]]
        ) { [weak self] _ in
            DispatchQueue.main.async {
                let list = self?.todos.isEmpty == true
                    ? "Empty"
                    : self?.todos.joined(separator: ", ") ?? ""
                self?.addLog("[LOCAL] Todos: \(list)")
            }
        }

        // Collect flows
        // Note: In real Swift code you'd use Kotlin's Flow adapters
        // (e.g., SKIE or KMP-NativeCoroutines). Below is pseudocode
        // showing the pattern — exact API depends on your interop setup.
        //
        // agent?.transcript.collect { text in
        //     self.transcript = text
        // }
        //
        // agent?.errors.collect { error in
        //     self.errorMessage = error.message
        //     self.addLog("Error: \(error.message)")
        // }
        //
        // agent?.actionResults.collect { result in
        //     switch result {
        //     case let success as ActionResult.Success:
        //         self.addLog("[\(success.scope)] \(success.intent): \(success.message)")
        //     case let error as ActionResult.Error_:
        //         self.addLog("[\(error.scope)] \(error.intent) FAILED: \(error.message)")
        //     }
        // }
    }

    func toggleListening() {
        if isListening {
            agent?.stop()
            isListening = false
            addLog("Stopped listening")
        } else {
            agent?.start()
            isListening = true
            addLog("Started listening...")
        }
    }

    func removeTodo(at index: Int) {
        let removed = todos.remove(at: index)
        addLog("Removed: \(removed)")
    }

    func setLanguage(_ lang: String) {
        language = lang
        let config = VoiceAgentConfig(language: lang)
        agent?.updateConfig(newConfig: config)
        addLog("[CONFIG] Language: \(lang)")
    }

    private func addLog(_ text: String) {
        let entry = "[\(timeString())] \(text)"
        logs.insert(entry, at: 0)
        if logs.count > 20 { logs.removeLast() }
    }

    private func timeString() -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "HH:mm:ss"
        return fmt.string(from: Date())
    }

    deinit {
        agent?.destroy()
    }
}

// MARK: - SwiftUI View

struct ContentView: View {
    @StateObject private var vm = VoiceAgentViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Title
                Text("V8V")
                    .font(.largeTitle.bold())
                Text("macOS Demo")
                    .foregroundColor(.secondary)

                // Mic button
                Button(action: { vm.toggleListening() }) {
                    Image(systemName: vm.isListening ? "mic.fill" : "mic")
                        .font(.system(size: 32))
                        .foregroundColor(.white)
                        .frame(width: 72, height: 72)
                        .background(vm.isListening ? Color.red : Color.blue)
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)

                Text(vm.isListening ? "Listening..." : "Click to start")
                    .foregroundColor(.secondary)
                    .font(.caption)

                // Transcript
                GroupBox("Transcript") {
                    Text(vm.transcript.isEmpty ? "No speech detected" : vm.transcript)
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                // Todo List
                GroupBox("Todo List") {
                    if vm.todos.isEmpty {
                        Text("Say \"add milk\" to get started")
                            .foregroundColor(.secondary)
                            .italic()
                    } else {
                        ForEach(Array(vm.todos.enumerated()), id: \.offset) { index, todo in
                            HStack {
                                Text(todo)
                                Spacer()
                                Button("Remove") { vm.removeTodo(at: index) }
                                    .foregroundColor(.red)
                                    .font(.caption)
                            }
                            .padding(.vertical, 2)
                        }
                    }
                }

                // Settings
                GroupBox("Settings") {
                    Picker("Language", selection: $vm.language) {
                        Text("English").tag("en")
                        Text("Hindi").tag("hi")
                        Text("Spanish").tag("es")
                    }
                    .onChange(of: vm.language) { newLang in
                        vm.setLanguage(newLang)
                    }
                }

                // Commands
                GroupBox("Voice Commands") {
                    VStack(alignment: .leading, spacing: 4) {
                        Label("\"add [item]\" — Add to todo list", systemImage: "plus.circle")
                        Label("\"remove [item]\" — Remove from list", systemImage: "minus.circle")
                        Label("\"list todos\" — Show all todos", systemImage: "list.bullet")
                    }
                    .font(.caption)
                    .foregroundColor(.secondary)
                }

                // Event Log
                GroupBox("Event Log") {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 2) {
                            ForEach(vm.logs, id: \.self) { log in
                                Text(log)
                                    .font(.system(.caption, design: .monospaced))
                                    .foregroundColor(.secondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(maxHeight: 150)
                }
            }
            .padding()
        }
    }
}
