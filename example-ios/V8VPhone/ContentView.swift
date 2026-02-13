/**
 * V8V — iOS SwiftUI Example
 *
 * Full-featured demo showcasing all three action scopes:
 *   - LOCAL  — "add [item]" → in-app todo list
 *   - MCP    — "create task [item]" → local MCP server (JSON-RPC)
 *   - REMOTE — "notify [message]" → n8n/webhook HTTP POST
 *
 * Also demonstrates: language switching, fuzzy matching, continuous mode,
 * settings panel, and the VoiceAgentCallbacks API for Flow bridging.
 *
 * Build the XCFramework first:
 *   ./gradlew :core:assembleV8VCoreReleaseXCFramework
 *
 * Then open V8VPhone.xcodeproj and hit Cmd+R.
 * Note: Speech recognition requires a real device (not simulator).
 */

import SwiftUI
import Speech
import V8VCore

// MARK: - V8V Agent View Model (uses VoiceAgentCallbacks)

@MainActor
class VoiceAgentViewModel: ObservableObject {

    // ── Observable state ──
    @Published var isListening = false
    @Published var transcript = ""
    @Published var transcriptIsFinal = false
    @Published var todos: [String] = []
    @Published var logs: [String] = []
    @Published var errorMessage = ""
    @Published var audioLevel: Float = 0.0

    // ── Settings ──
    @Published var language = "en-US"
    @Published var continuous = true
    @Published var fuzzyThreshold: Float = 0.3
    @Published var mcpUrl = "http://localhost:3001/mcp"
    @Published var webhookUrl = ""

    private var agent: VoiceAgentCallbacks?

    init() {
        setupAgent()
    }

    private func setupAgent() {
        let engine = IosSpeechEngine()
        let config = VoiceAgentConfig(
            language: language,
            continuous: continuous,
            partialResults: true,
            fuzzyThreshold: fuzzyThreshold,
            silenceTimeoutMs: 1500
        )
        let permissionHelper = IosPermissionHelper()

        let a = VoiceAgentCallbacks(
            engine: engine,
            config: config,
            permissionHelper: permissionHelper
        )

        // ── Callback-based event observation (bridges Kotlin Flows → Swift) ──

        a.onTranscript { [weak self] text in
            DispatchQueue.main.async {
                self?.transcript = text
            }
        }

        a.onError { [weak self] msg in
            DispatchQueue.main.async {
                self?.errorMessage = msg
                self?.addLog("[ERROR] \(msg)")
            }
        }

        a.onUnhandled { [weak self] text in
            DispatchQueue.main.async {
                self?.transcriptIsFinal = true
                self?.addLog("[UNHANDLED] \"\(text)\"")
            }
        }

        a.onStateChange { [weak self] state in
            DispatchQueue.main.async {
                self?.addLog("[STATE] \(state)")
                self?.isListening = (state == "LISTENING")
                if state == "PROCESSING" {
                    self?.transcriptIsFinal = true
                }
                if state == "LISTENING" {
                    self?.transcriptIsFinal = false
                    self?.transcript = ""
                }
            }
        }

        a.onAudioLevel { [weak self] level in
            DispatchQueue.main.async {
                self?.audioLevel = level.floatValue
            }
        }

        // ────────────────────────────────────────────
        // 1. LOCAL: "add <item>" → in-app todo list
        // ────────────────────────────────────────────
        a.registerAction(
            intent: "todo.add",
            phrases: [
                "en-US": ["add *", "add * to todo", "add * to do", "add * to my list", "add * to the list", "todo *"],
                "hi-IN": ["* todo mein add karo", "todo mein * add karo"],
                "es-ES": ["agregar *", "agregar * a la lista"],
            ],
            handler: { [weak self] resolved in
                DispatchQueue.main.async {
                    self?.todos.append(resolved.extractedText)
                    self?.addLog("[LOCAL] Added: \(resolved.extractedText)")
                }
            }
        )

        // LOCAL: "remove <item>"
        a.registerAction(
            intent: "todo.remove",
            phrases: [
                "en-US": ["remove *", "delete *"],
                "es-ES": ["eliminar *", "quitar *"],
            ],
            handler: { [weak self] resolved in
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
        )

        // LOCAL: "list todos"
        a.registerAction(
            intent: "todo.list",
            phrases: [
                "en-US": ["list todos", "show todos", "show my list"],
            ],
            handler: { [weak self] _ in
                DispatchQueue.main.async {
                    let list = self?.todos.isEmpty == true
                        ? "Empty"
                        : self?.todos.joined(separator: ", ") ?? ""
                    self?.addLog("[LOCAL] Todos: \(list)")
                }
            }
        )

        // ────────────────────────────────────────────
        // 2. MCP: "create task <item>" → local MCP server
        // ────────────────────────────────────────────
        a.registerAction(
            intent: "task.create",
            phrases: [
                "en-US": ["create task *", "new task *", "task *"],
            ],
            handler: { [weak self] resolved in
                DispatchQueue.main.async {
                    guard let self = self else { return }
                    self.addLog("[MCP] create_task(\"\(resolved.extractedText)\")")
                    self.callMcpTool(toolName: "create_task", text: resolved.extractedText)
                }
            }
        )

        // ────────────────────────────────────────────
        // 3. REMOTE: "notify <message>" → webhook (n8n)
        // ────────────────────────────────────────────
        a.registerAction(
            intent: "notify.team",
            phrases: [
                "en-US": ["notify *", "send notification *", "alert *"],
            ],
            handler: { [weak self] resolved in
                DispatchQueue.main.async {
                    guard let self = self else { return }
                    self.addLog("[REMOTE] webhook: \"\(resolved.extractedText)\"")
                    self.callWebhook(intent: "notify.team", text: resolved.extractedText)
                }
            }
        )

        agent = a
        addLog("V8V Agent ready — say a command")
    }

    // ── Control ──

    func toggleListening() {
        if isListening {
            agent?.stop()
            addLog("Stopped")
        } else {
            SFSpeechRecognizer.requestAuthorization { [weak self] authStatus in
                DispatchQueue.main.async {
                    guard let self = self else { return }
                    switch authStatus {
                    case .authorized:
                        self.agent?.start()
                        self.addLog("Starting...")
                    case .denied, .restricted:
                        self.addLog("ERROR: Speech permission denied")
                        self.errorMessage = "Go to Settings → Privacy → Speech Recognition."
                    case .notDetermined:
                        self.addLog("ERROR: Permission not determined")
                    @unknown default:
                        self.addLog("ERROR: Unknown permission")
                    }
                }
            }
        }
    }

    func removeTodo(at index: Int) {
        guard index < todos.count else { return }
        let removed = todos.remove(at: index)
        addLog("Removed: \(removed)")
    }

    // ── Settings ──

    func applySettings() {
        let config = VoiceAgentConfig(
            language: language,
            continuous: continuous,
            partialResults: true,
            fuzzyThreshold: fuzzyThreshold,
            silenceTimeoutMs: 1500
        )
        agent?.updateConfig(newConfig: config)
        addLog("[CONFIG] lang=\(language), continuous=\(continuous), fuzzy=\(String(format: "%.1f", fuzzyThreshold))")
    }

    // ── MCP (JSON-RPC 2.0) ──

    private func callMcpTool(toolName: String, text: String) {
        guard !mcpUrl.isEmpty, let url = URL(string: mcpUrl) else {
            addLog("[MCP] No URL configured")
            return
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body: [String: Any] = [
            "jsonrpc": "2.0", "id": 1, "method": "tools/call",
            "params": ["name": toolName, "arguments": ["text": text]]
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    self?.addLog("[MCP] Failed: \(error.localizedDescription)")
                } else {
                    let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                    self?.addLog("[MCP] Response: HTTP \(status)")
                }
            }
        }.resume()
    }

    // ── Webhook (HTTP POST) ──

    private func callWebhook(intent: String, text: String) {
        guard !webhookUrl.isEmpty, let url = URL(string: webhookUrl) else {
            addLog("[REMOTE] No webhook URL — set in Settings")
            return
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body: [String: Any] = [
            "intent": intent, "extractedText": text,
            "language": language,
            "timestamp": ISO8601DateFormatter().string(from: Date())
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    self?.addLog("[REMOTE] Failed: \(error.localizedDescription)")
                } else {
                    let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                    self?.addLog("[REMOTE] HTTP \(status)")
                }
            }
        }.resume()
    }

    // ── Logging ──

    private func addLog(_ text: String) {
        let fmt = DateFormatter()
        fmt.dateFormat = "HH:mm:ss"
        let entry = "[\(fmt.string(from: Date()))] \(text)"
        logs.insert(entry, at: 0)
        if logs.count > 30 { logs.removeLast() }
    }

    deinit {
        agent?.destroy()
    }
}

// MARK: - SwiftUI View

struct ContentView: View {
    @StateObject private var vm = VoiceAgentViewModel()
    @State private var showSettings = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {

                    // ── Mic Button ──
                    VStack(spacing: 8) {
                        ZStack {
                            Circle()
                                .stroke(lineWidth: 4)
                                .foregroundColor(vm.isListening ? .red.opacity(Double(vm.audioLevel)) : .clear)
                                .frame(width: 96, height: 96)
                                .animation(.easeOut(duration: 0.1), value: vm.audioLevel)

                            Button(action: { vm.toggleListening() }) {
                                Image(systemName: vm.isListening ? "mic.fill" : "mic")
                                    .font(.system(size: 36))
                                    .foregroundColor(.white)
                                    .frame(width: 80, height: 80)
                                    .background(vm.isListening ? Color.red : Color.accentColor)
                                    .clipShape(Circle())
                                    .shadow(radius: 4)
                            }
                        }

                        Text(vm.isListening ? "Listening... (pause to trigger)" : "Tap to start")
                            .foregroundColor(.secondary)
                            .font(.caption)
                    }
                    .padding(.top, 8)

                    // ── Error banner ──
                    if !vm.errorMessage.isEmpty {
                        HStack {
                            Image(systemName: "exclamationmark.triangle.fill")
                            Text(vm.errorMessage).font(.caption)
                        }
                        .foregroundColor(.red)
                        .padding(8)
                        .frame(maxWidth: .infinity)
                        .background(Color.red.opacity(0.1))
                        .cornerRadius(8)
                    }

                    // ── Transcript ──
                    VStack(alignment: .leading, spacing: 6) {
                        Label("Transcript", systemImage: "text.quote")
                            .font(.headline)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(vm.transcript.isEmpty ? "No speech detected" : vm.transcript)
                                .foregroundColor(vm.transcriptIsFinal ? .primary : .secondary)
                            if !vm.transcript.isEmpty && !vm.transcriptIsFinal {
                                Text("(partial — pause to finalize)")
                                    .font(.caption2)
                                    .foregroundColor(.orange)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(Color(.systemGray6))
                        .cornerRadius(10)
                    }

                    // ── Task List ──
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Label("Task List", systemImage: "checklist")
                                .font(.headline)
                            Spacer()
                            BadgeView(text: "LOCAL", color: .purple)
                        }

                        if vm.todos.isEmpty {
                            Text("Say \"add project status update\" to get started")
                                .foregroundColor(.secondary)
                                .italic()
                                .padding()
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color(.systemGray6))
                                .cornerRadius(10)
                        } else {
                            VStack(spacing: 0) {
                                ForEach(Array(vm.todos.enumerated()), id: \.offset) { index, todo in
                                    HStack {
                                        Image(systemName: "circle")
                                            .font(.caption2)
                                            .foregroundColor(.accentColor)
                                        Text(todo)
                                        Spacer()
                                        Button(role: .destructive) {
                                            vm.removeTodo(at: index)
                                        } label: {
                                            Image(systemName: "trash")
                                                .font(.caption)
                                        }
                                    }
                                    .padding(.horizontal)
                                    .padding(.vertical, 10)
                                    if index < vm.todos.count - 1 { Divider() }
                                }
                            }
                            .background(Color(.systemGray6))
                            .cornerRadius(10)
                        }
                    }

                    // ── Voice Commands ──
                    VStack(alignment: .leading, spacing: 6) {
                        Label("Commands", systemImage: "waveform")
                            .font(.headline)
                        VStack(alignment: .leading, spacing: 8) {
                            CommandRowView(scope: "LOCAL", color: .purple, phrases: "\"add/remove/list\"")
                            CommandRowView(scope: "MCP", color: .orange, phrases: "\"create task [item]\"")
                            CommandRowView(scope: "REMOTE", color: .blue, phrases: "\"notify [msg]\"")
                        }
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.systemGray6))
                        .cornerRadius(10)
                    }

                    // ── Event Log ──
                    VStack(alignment: .leading, spacing: 6) {
                        Label("Event Log", systemImage: "doc.text")
                            .font(.headline)
                        ScrollView {
                            VStack(alignment: .leading, spacing: 2) {
                                ForEach(vm.logs, id: \.self) { log in
                                    Text(log)
                                        .font(.system(.caption2, design: .monospaced))
                                        .foregroundColor(logColor(for: log))
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .frame(maxHeight: 140)
                        .padding()
                        .background(Color(.systemGray6))
                        .cornerRadius(10)
                    }
                }
                .padding()
            }
            .navigationTitle("V8V")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showSettings.toggle() }) {
                        Image(systemName: "gearshape")
                    }
                }
            }
            .sheet(isPresented: $showSettings) {
                SettingsSheet(vm: vm)
            }
        }
    }

    private func logColor(for log: String) -> Color {
        if log.contains("[ERROR]") { return .red }
        if log.contains("[LOCAL]") { return .purple }
        if log.contains("[MCP]") { return .orange }
        if log.contains("[REMOTE]") { return .blue }
        if log.contains("[UNHANDLED]") { return .yellow }
        return .secondary
    }
}

// MARK: - Settings Sheet

struct SettingsSheet: View {
    @ObservedObject var vm: VoiceAgentViewModel
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Speech") {
                    Picker("Language", selection: $vm.language) {
                        Text("English (US)").tag("en-US")
                        Text("Hindi").tag("hi-IN")
                        Text("Spanish").tag("es-ES")
                    }
                    .onChange(of: vm.language) { _ in vm.applySettings() }

                    Toggle("Continuous mode", isOn: $vm.continuous)
                        .onChange(of: vm.continuous) { _ in vm.applySettings() }

                    VStack(alignment: .leading) {
                        Text("Fuzzy threshold: \(String(format: "%.1f", vm.fuzzyThreshold))")
                        Slider(value: $vm.fuzzyThreshold, in: 0...1, step: 0.1)
                            .onChange(of: vm.fuzzyThreshold) { _ in vm.applySettings() }
                        Text("0.0 = exact only, higher = more lenient")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }

                Section("Connectors") {
                    VStack(alignment: .leading) {
                        Text("MCP Server URL")
                            .font(.caption)
                            .foregroundColor(.orange)
                        TextField("http://localhost:3001/mcp", text: $vm.mcpUrl)
                            .font(.system(.caption, design: .monospaced))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }

                    VStack(alignment: .leading) {
                        Text("Webhook URL (n8n)")
                            .font(.caption)
                            .foregroundColor(.blue)
                        TextField("https://n8n.example.com/webhook/voice", text: $vm.webhookUrl)
                            .font(.system(.caption, design: .monospaced))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                }

                Section("How Fuzzy Matching Works") {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Pass 1: Wildcard regex").font(.caption.bold())
                        Text("\"add *\" becomes /^add (.+)$/").font(.caption).foregroundColor(.secondary)

                        Text("Pass 2: Dice similarity").font(.caption.bold())
                        Text("Dice = (2 x |intersection|) / (|A| + |B|)").font(.system(.caption, design: .monospaced)).foregroundColor(.secondary)
                        Text("Penalizes filler words. Higher threshold = stricter.").font(.caption2).foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Reusable UI

struct BadgeView: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.system(.caption2, design: .rounded).bold())
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.2))
            .foregroundColor(color)
            .cornerRadius(4)
    }
}

struct CommandRowView: View {
    let scope: String
    let color: Color
    let phrases: String

    var body: some View {
        HStack(spacing: 8) {
            BadgeView(text: scope, color: color)
            Text(phrases)
                .font(.caption)
                .foregroundColor(.secondary)
                .italic()
        }
    }
}
