/**
 * V8V — macOS SwiftUI Example
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
 * Then open V8VMac.xcodeproj and hit Cmd+R.
 */

import SwiftUI
import Speech
import AVFoundation
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

    // ── Connector status ──
    @Published var mcpStatus = "Not tested"
    @Published var webhookStatus = "Not configured"

    private var agent: VoiceAgentCallbacks?

    init() {
        setupAgent()
    }

    private func setupAgent() {
        let engine = MacosSpeechEngine()
        let config = VoiceAgentConfig(
            language: language,
            continuous: continuous,
            partialResults: true,
            fuzzyThreshold: fuzzyThreshold,
            silenceTimeoutMs: 1500
        )
        let permissionHelper = MacosPermissionHelper()

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
                self?.addLog("[ERROR] \(msg)", style: .error)
            }
        }

        a.onUnhandled { [weak self] text in
            DispatchQueue.main.async {
                self?.transcriptIsFinal = true
                self?.addLog("[UNHANDLED] \"\(text)\" — no intent matched", style: .unhandled)
            }
        }

        a.onStateChange { [weak self] state in
            DispatchQueue.main.async {
                self?.addLog("[STATE] \(state)", style: .system)
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
                    self?.addLog("[LOCAL] Added: \(resolved.extractedText)", style: .local)
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
                        self.addLog("[LOCAL] Removed: \(removed)", style: .local)
                    } else {
                        self.addLog("[LOCAL] Not found: \(resolved.extractedText)", style: .unhandled)
                    }
                }
            }
        )

        // LOCAL: "list todos"
        a.registerAction(
            intent: "todo.list",
            phrases: [
                "en-US": ["list todos", "show todos", "show my list", "what's on my list"],
            ],
            handler: { [weak self] _ in
                DispatchQueue.main.async {
                    let list = self?.todos.isEmpty == true
                        ? "Empty"
                        : self?.todos.joined(separator: ", ") ?? ""
                    self?.addLog("[LOCAL] Todos: \(list)", style: .local)
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
                    self.addLog("[MCP] Calling create_task(\"\(resolved.extractedText)\")...", style: .mcp)
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
                    self.addLog("[REMOTE] Sending webhook: \"\(resolved.extractedText)\"...", style: .remote)
                    self.callWebhook(intent: "notify.team", text: resolved.extractedText)
                }
            }
        )

        agent = a
        addLog("V8V Agent ready — say a command", style: .system)
        addLog("Tip: pause briefly after speaking for intent to trigger", style: .system)
    }

    // ── Control ──

    func toggleListening() {
        if isListening {
            agent?.stop()
            addLog("Stopped", style: .system)
        } else {
            SFSpeechRecognizer.requestAuthorization { [weak self] authStatus in
                DispatchQueue.main.async {
                    guard let self = self else { return }
                    switch authStatus {
                    case .authorized:
                        self.agent?.start()
                        self.addLog("Starting...", style: .system)
                    case .denied, .restricted:
                        self.addLog("ERROR: Speech permission denied", style: .error)
                        self.errorMessage = "Speech recognition permission denied."
                    case .notDetermined:
                        self.addLog("ERROR: Speech permission not determined", style: .error)
                    @unknown default:
                        self.addLog("ERROR: Unknown permission", style: .error)
                    }
                }
            }
        }
    }

    func removeTodo(at index: Int) {
        guard index < todos.count else { return }
        let removed = todos.remove(at: index)
        addLog("Removed: \(removed)", style: .local)
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
        addLog("[CONFIG] lang=\(language), continuous=\(continuous), fuzzy=\(String(format: "%.1f", fuzzyThreshold))", style: .system)
    }

    // ── MCP (JSON-RPC 2.0 call to local MCP server) ──

    func testMcpConnection() {
        guard !mcpUrl.isEmpty, let url = URL(string: mcpUrl) else {
            mcpStatus = "Invalid URL"
            return
        }
        mcpStatus = "Testing..."
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 5
        // Send an initialize request to check if server is alive
        let body: [String: Any] = [
            "jsonrpc": "2.0", "id": 0, "method": "initialize",
            "params": ["protocolVersion": "2024-11-05", "capabilities": [:],
                       "clientInfo": ["name": "v8v-mac", "version": "1.0.0"]]
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    self?.mcpStatus = "Offline: \(error.localizedDescription)"
                    self?.addLog("[MCP] Test failed: \(error.localizedDescription)", style: .error)
                    return
                }
                let httpStatus = (response as? HTTPURLResponse)?.statusCode ?? 0
                if httpStatus == 200 {
                    self?.mcpStatus = "Connected (HTTP 200)"
                    self?.addLog("[MCP] Server reachable at \(url.absoluteString)", style: .mcp)
                } else {
                    self?.mcpStatus = "HTTP \(httpStatus)"
                    self?.addLog("[MCP] Server responded: HTTP \(httpStatus)", style: .mcp)
                }
            }
        }.resume()
    }

    private func callMcpTool(toolName: String, text: String) {
        guard !mcpUrl.isEmpty else {
            mcpStatus = "No URL"
            addLog("[MCP] No MCP URL configured", style: .error)
            return
        }

        guard let url = URL(string: mcpUrl) else {
            mcpStatus = "Invalid URL"
            addLog("[MCP] Invalid URL: \(mcpUrl)", style: .error)
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "jsonrpc": "2.0",
            "id": 1,
            "method": "tools/call",
            "params": [
                "name": toolName,
                "arguments": ["text": text]
            ]
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    self?.mcpStatus = "Error"
                    self?.addLog("[MCP] Failed: \(error.localizedDescription)", style: .error)
                    return
                }
                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                if let data = data, let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    self?.mcpStatus = "OK (\(status))"
                    self?.addLog("[MCP] Response (\(status)): \(json["result"] ?? json)", style: .mcp)
                } else {
                    self?.mcpStatus = "HTTP \(status)"
                    self?.addLog("[MCP] Response: HTTP \(status)", style: .mcp)
                }
            }
        }.resume()
    }

    // ── Webhook (HTTP POST to n8n or similar) ──

    func testWebhookConnection() {
        guard !webhookUrl.isEmpty, let url = URL(string: webhookUrl) else {
            webhookStatus = webhookUrl.isEmpty ? "Not configured" : "Invalid URL"
            return
        }
        webhookStatus = "Testing..."
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 5
        let body: [String: Any] = [
            "intent": "test.ping", "extractedText": "connection test",
            "language": language,
            "timestamp": ISO8601DateFormatter().string(from: Date())
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    self?.webhookStatus = "Offline: \(error.localizedDescription)"
                    self?.addLog("[REMOTE] Test failed: \(error.localizedDescription)", style: .error)
                    return
                }
                let httpStatus = (response as? HTTPURLResponse)?.statusCode ?? 0
                self?.webhookStatus = httpStatus == 200 ? "Connected (HTTP 200)" : "HTTP \(httpStatus)"
                self?.addLog("[REMOTE] Webhook test: HTTP \(httpStatus)", style: .remote)
            }
        }.resume()
    }

    private func callWebhook(intent: String, text: String) {
        guard !webhookUrl.isEmpty else {
            webhookStatus = "Not configured"
            addLog("[REMOTE] No webhook URL — set one in Settings", style: .error)
            return
        }

        guard let url = URL(string: webhookUrl) else {
            webhookStatus = "Invalid URL"
            addLog("[REMOTE] Invalid URL: \(webhookUrl)", style: .error)
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "intent": intent,
            "extractedText": text,
            "language": language,
            "timestamp": ISO8601DateFormatter().string(from: Date())
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    self?.webhookStatus = "Error"
                    self?.addLog("[REMOTE] Failed: \(error.localizedDescription)", style: .error)
                    return
                }
                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                self?.webhookStatus = "OK (\(status))"
                self?.addLog("[REMOTE] Webhook responded: HTTP \(status)", style: .remote)
            }
        }.resume()
    }

    // ── Logging ──

    enum LogStyle { case system, local, mcp, remote, error, unhandled }

    private func addLog(_ text: String, style: LogStyle) {
        let prefix: String
        switch style {
        case .system:    prefix = ""
        case .local:     prefix = ""
        case .mcp:       prefix = ""
        case .remote:    prefix = ""
        case .error:     prefix = ""
        case .unhandled: prefix = ""
        }
        let fmt = DateFormatter()
        fmt.dateFormat = "HH:mm:ss"
        let entry = "[\(fmt.string(from: Date()))] \(prefix)\(text)"
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

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {

                // ── Title ──
                Text("V8V")
                    .font(.largeTitle.bold())
                Text("macOS Demo — LOCAL + MCP + REMOTE")
                    .foregroundColor(.secondary)
                    .font(.caption)

                // ══════════════════════════════
                // Mic Button + Audio Level
                // ══════════════════════════════
                VStack(spacing: 8) {
                    ZStack {
                        // Audio level ring
                        Circle()
                            .stroke(lineWidth: 4)
                            .foregroundColor(vm.isListening ? .red.opacity(Double(vm.audioLevel)) : .clear)
                            .frame(width: 88, height: 88)
                            .animation(.easeOut(duration: 0.1), value: vm.audioLevel)

                        Button(action: { vm.toggleListening() }) {
                            Image(systemName: vm.isListening ? "mic.fill" : "mic")
                                .font(.system(size: 32))
                                .foregroundColor(.white)
                                .frame(width: 72, height: 72)
                                .background(vm.isListening ? Color.red : Color.accentColor)
                                .clipShape(Circle())
                        }
                        .buttonStyle(.plain)
                    }

                    Text(vm.isListening ? "Listening... (pause to trigger intent)" : "Click to start")
                        .foregroundColor(.secondary)
                        .font(.caption)
                }

                // ══════════════════════════════
                // Transcript
                // ══════════════════════════════
                GroupBox("Transcript") {
                    VStack(alignment: .leading, spacing: 4) {
                        if vm.transcript.isEmpty {
                            Text("No speech detected")
                                .foregroundColor(.secondary)
                                .italic()
                        } else {
                            Text(vm.transcript)
                                .foregroundColor(vm.transcriptIsFinal ? .primary : .secondary)
                            if !vm.transcriptIsFinal {
                                Text("(partial — pause to finalize)")
                                    .font(.caption2)
                                    .foregroundColor(.orange)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                // ══════════════════════════════
                // Error banner
                // ══════════════════════════════
                if !vm.errorMessage.isEmpty {
                    HStack {
                        Image(systemName: "exclamationmark.triangle.fill")
                        Text(vm.errorMessage)
                            .font(.caption)
                    }
                    .foregroundColor(.red)
                    .padding(8)
                    .frame(maxWidth: .infinity)
                    .background(Color.red.opacity(0.1))
                    .cornerRadius(8)
                }

                // ══════════════════════════════
                // Task List (LOCAL scope)
                // ══════════════════════════════
                GroupBox {
                    VStack(alignment: .leading, spacing: 4) {
                        if vm.todos.isEmpty {
                            Text("Say \"add project status update\" to get started")
                                .foregroundColor(.secondary)
                                .italic()
                        } else {
                            ForEach(Array(vm.todos.enumerated()), id: \.offset) { index, todo in
                                HStack {
                                    Image(systemName: "circle")
                                        .font(.caption2)
                                        .foregroundColor(.accentColor)
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
                } label: {
                    HStack {
                        Text("Task List")
                        Spacer()
                        Badge(text: "LOCAL", color: .purple)
                    }
                }

                // ══════════════════════════════
                // Voice Commands Reference
                // ══════════════════════════════
                GroupBox("Registered Commands") {
                    VStack(alignment: .leading, spacing: 6) {
                        CommandRow(scope: "LOCAL", color: .purple, phrases: [
                            "\"add [item]\"", "\"remove [item]\"", "\"list todos\""
                        ])
                        CommandRow(scope: "MCP", color: .orange, phrases: [
                            "\"create task [item]\"", "\"new task [item]\""
                        ])
                        CommandRow(scope: "REMOTE", color: .blue, phrases: [
                            "\"notify [message]\"", "\"alert [message]\""
                        ])
                    }
                }

                // ══════════════════════════════
                // Settings
                // ══════════════════════════════
                GroupBox("Settings") {
                    VStack(spacing: 10) {
                        // Language
                        HStack {
                            Label("Language", systemImage: "globe")
                                .font(.caption)
                            Spacer()
                            Picker("", selection: $vm.language) {
                                Text("English (US)").tag("en-US")
                                Text("Hindi").tag("hi-IN")
                                Text("Spanish").tag("es-ES")
                            }
                            .frame(width: 150)
                            .onChange(of: vm.language) { _ in vm.applySettings() }
                        }

                        // Continuous mode
                        HStack {
                            Label("Continuous", systemImage: "repeat")
                                .font(.caption)
                            Spacer()
                            Toggle("", isOn: $vm.continuous)
                                .toggleStyle(.switch)
                                .onChange(of: vm.continuous) { _ in vm.applySettings() }
                        }

                        // Fuzzy threshold
                        HStack {
                            Label("Fuzzy: \(String(format: "%.1f", vm.fuzzyThreshold))", systemImage: "wand.and.stars")
                                .font(.caption)
                            Spacer()
                            Slider(value: $vm.fuzzyThreshold, in: 0...1, step: 0.1)
                                .frame(width: 150)
                                .onChange(of: vm.fuzzyThreshold) { _ in vm.applySettings() }
                        }

                        Divider()

                        // MCP URL + status
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Label("MCP Server", systemImage: "server.rack")
                                    .font(.caption)
                                    .foregroundColor(.orange)
                                Spacer()
                                Text(vm.mcpStatus)
                                    .font(.caption2)
                                    .foregroundColor(vm.mcpStatus.contains("Connected") ? .green : .secondary)
                                Button("Test") { vm.testMcpConnection() }
                                    .font(.caption2)
                                    .buttonStyle(.bordered)
                            }
                            TextField("http://localhost:3001/mcp", text: $vm.mcpUrl)
                                .textFieldStyle(.roundedBorder)
                                .font(.system(.caption, design: .monospaced))
                        }

                        // Webhook URL + status
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Label("Webhook (n8n)", systemImage: "arrow.up.forward.app")
                                    .font(.caption)
                                    .foregroundColor(.blue)
                                Spacer()
                                Text(vm.webhookStatus)
                                    .font(.caption2)
                                    .foregroundColor(vm.webhookStatus.contains("Connected") ? .green : .secondary)
                                Button("Test") { vm.testWebhookConnection() }
                                    .font(.caption2)
                                    .buttonStyle(.bordered)
                            }
                            TextField("https://n8n.example.com/webhook/voice", text: $vm.webhookUrl)
                                .textFieldStyle(.roundedBorder)
                                .font(.system(.caption, design: .monospaced))
                        }
                    }
                }

                // ══════════════════════════════
                // How Fuzzy Matching Works
                // ══════════════════════════════
                GroupBox("How Fuzzy Matching Works") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Pass 1 — Wildcard regex match:")
                            .font(.caption.bold())
                        Text("Pattern `add * to todo` becomes regex `^add (.+) to todo$`. If input matches exactly, confidence = 1.0 and `*` is the extracted text.")
                            .font(.caption)
                            .foregroundColor(.secondary)

                        Text("Pass 2 — Dice similarity (when threshold > 0):")
                            .font(.caption.bold())
                        Text("Dice = (2 x |intersection|) / (|A| + |B|)")
                            .font(.system(.caption, design: .monospaced))
                            .foregroundColor(.secondary)

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Example: \"add milk to todo\" (4 words)")
                                .font(.caption).foregroundColor(.secondary)
                            Text("Pattern literal words: {add, to, todo} (3 words)")
                                .font(.caption).foregroundColor(.secondary)
                            Text("Dice = (2 x 3) / (4 + 3) = 0.86 — match at threshold 0.5")
                                .font(.system(.caption, design: .monospaced))
                                .foregroundColor(.green)
                        }
                        .padding(8)
                        .background(Color.secondary.opacity(0.1))
                        .cornerRadius(6)

                        Text("Higher threshold = stricter matching. 0.0 disables fuzzy (exact only).")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                // ══════════════════════════════
                // Event Log
                // ══════════════════════════════
                GroupBox("Event Log") {
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
                    .frame(maxHeight: 180)
                }
            }
            .padding()
        }
        .frame(minWidth: 500, minHeight: 700)
    }

    private func logColor(for log: String) -> Color {
        if log.contains("[ERROR]") { return .red }
        if log.contains("[LOCAL]") { return .purple }
        if log.contains("[MCP]") { return .orange }
        if log.contains("[REMOTE]") { return .blue }
        if log.contains("[UNHANDLED]") { return .yellow }
        if log.contains("[TRANSCRIPT]") { return .cyan }
        return .secondary
    }
}

// MARK: - Reusable UI Components

struct Badge: View {
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

struct CommandRow: View {
    let scope: String
    let color: Color
    let phrases: [String]

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Badge(text: scope, color: color)
            Text(phrases.joined(separator: " / "))
                .font(.caption)
                .foregroundColor(.secondary)
                .italic()
        }
    }
}
