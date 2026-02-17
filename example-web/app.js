/**
 * V8V Example — Web app using the v8v-core library.
 *
 * Demonstrates all three action scopes:
 *   1. LOCAL  — "add <item>" → in-app task list (JS handler)
 *   2. MCP    — "create task <item>" → MCP server (library McpClient via Ktor)
 *   3. REMOTE — "notify <message>" → webhook (library WebhookActionHandler via Ktor)
 *
 * All three action scopes use the v8v-core library engine, including MCP/webhook
 * HTTP calls which use the library's built-in Ktor HTTP client (Ktor's JS engine
 * uses the browser's fetch() API internally, so CORS behaviour is identical).
 */

// ═══════════════════════════════════════════════════════════════════
// State
// ═══════════════════════════════════════════════════════════════════

let listening = false;
let recognition = null;
let todos = [];
let agent = null;
let usingLibrary = false;
let micPermissionGranted = false;

const config = {
    language: 'en',
    continuous: false,
    fuzzyThreshold: 0.0,
    mcpServerUrl: '',
    webhookUrl: '',
};

// Intent metadata for fallback and scope lookup
const intents = [
    { name: 'todo.add', scope: 'LOCAL', patterns: ['add *', 'add * to todo', 'add * to my list'] },
    { name: 'todo.remove', scope: 'LOCAL', patterns: ['remove *', 'delete *'] },
    { name: 'todo.list', scope: 'LOCAL', patterns: ['list todos', 'show todos', 'show my list'] },
    { name: 'task.create', scope: 'MCP', patterns: ['create task *', 'new task *', 'task *'] },
    { name: 'notify.team', scope: 'REMOTE', patterns: ['notify *', 'send notification *', 'alert *'] },
];

// ═══════════════════════════════════════════════════════════════════
// DOM refs
// ═══════════════════════════════════════════════════════════════════

const micBtn = document.getElementById('micBtn');
const statusEl = document.getElementById('status');
const transcriptEl = document.getElementById('transcript');
const logEl = document.getElementById('log');
const loadStatusEl = document.getElementById('loadStatus');
const langSelect = document.getElementById('langSelect');
const continuousCheck = document.getElementById('continuousCheck');
const fuzzySlider = document.getElementById('fuzzySlider');
const fuzzyValue = document.getElementById('fuzzyValue');
const mcpUrlInput = document.getElementById('mcpUrl');
const webhookUrlInput = document.getElementById('webhookUrl');
const todoListEl = document.getElementById('todoList');

// ═══════════════════════════════════════════════════════════════════
// Load v8v-core library
// ═══════════════════════════════════════════════════════════════════

function tryLoadLibrary() {
    try {
        const mod = globalThis['io.github.alimomin1998:core'];

        if (mod) {
            const VoiceAgentJs = mod.io.v8v.core.VoiceAgentJs;

            if (VoiceAgentJs) {
                agent = new VoiceAgentJs(config.language);

                // ── Register LOCAL intents ──
                agent.registerPhrase('todo.add', 'en', 'add *');
                agent.registerPhrase('todo.add', 'en', 'add * to todo');
                agent.registerPhrase('todo.add', 'en', 'add * to my list');
                agent.registerPhrase('todo.remove', 'en', 'remove *');
                agent.registerPhrase('todo.remove', 'en', 'delete *');
                agent.registerPhrase('todo.list', 'en', 'list todos');
                agent.registerPhrase('todo.list', 'en', 'show todos');

                // MCP + REMOTE intents are registered when URLs are configured
                // (see updateMcpUrl and updateWebhookUrl below). They use the
                // library's built-in Ktor-based McpClient / WebhookActionHandler.

                // ── Callbacks ──
                agent.onTranscript(text => {
                    transcriptEl.textContent = text;
                    addLog(`Transcript: "${text}"`, 'info');
                });

                // Register error callback BEFORE onIntent so action errors
                // can be forwarded (onIntent forwards ActionResult.Error to onError).
                agent.onError(msg => addLog(`Error: ${msg}`, 'error'));

                agent.onIntent((intentName, message) => {
                    const intent = intents.find(i => i.name === intentName);
                    const scope = intent ? intent.scope : 'LOCAL';

                    // LOCAL: todo management (message = extractedText from library)
                    if (intentName === 'todo.add' && message) {
                        todos.push(message);
                        addLog(`[LOCAL] Added "${message}"`, 'intent');
                        renderTodos();
                    } else if (intentName === 'todo.remove' && message) {
                        const idx = todos.findIndex(t =>
                            t.toLowerCase().includes(message.toLowerCase()));
                        if (idx >= 0) {
                            const removed = todos.splice(idx, 1)[0];
                            addLog(`[LOCAL] Removed "${removed}"`, 'intent');
                            renderTodos();
                        } else {
                            addLog(`[LOCAL] "${message}" not found in list`, 'error');
                        }
                    } else if (intentName === 'todo.list') {
                        addLog(todos.length
                            ? `[LOCAL] Tasks: ${todos.join(', ')}`
                            : '[LOCAL] Task list is empty', 'intent');

                    // MCP: result comes from library's McpActionHandler (Ktor HTTP)
                    } else if (scope === 'MCP') {
                        addLog(`[MCP] ${message || 'OK'}`, 'intent');

                    // REMOTE: result comes from library's WebhookActionHandler (Ktor HTTP)
                    } else if (scope === 'REMOTE') {
                        addLog(`[REMOTE] ${message || 'Delivered'}`, 'intent');

                    } else {
                        addLog(`[${scope}] ${intentName}: ${message}`, 'intent');
                    }
                });

                agent.onUnhandled(text => addLog(`Unmatched: "${text}"`, 'error'));

                usingLibrary = true;
                loadStatusEl.textContent =
                    'v8v-core loaded — using VoiceAgentJs with LOCAL + MCP + REMOTE (all via library)';
                loadStatusEl.className = 'load-ok';
                addLog('[LIB] VoiceAgentJs loaded (single package, full parity)', 'intent');
                return;
            }
        }
    } catch (e) {
        console.warn('Could not load v8v-core:', e);
    }

    // Fallback
    usingLibrary = false;
    loadStatusEl.textContent = 'v8v-core not found — using Web Speech API fallback';
    loadStatusEl.className = 'load-fail';
    addLog('[FALLBACK] Using Web Speech API + inline intent matching', 'error');
}

tryLoadLibrary();

// ═══════════════════════════════════════════════════════════════════
// Logging
// ═══════════════════════════════════════════════════════════════════

function addLog(text, className) {
    const line = document.createElement('div');
    line.textContent = `[${new Date().toLocaleTimeString()}] ${text}`;
    if (className) line.className = className;
    logEl.prepend(line);
    while (logEl.children.length > 30) logEl.removeChild(logEl.lastChild);
}

// ═══════════════════════════════════════════════════════════════════
// Mic toggle
// ═══════════════════════════════════════════════════════════════════

function toggleMic() {
    listening ? stopListening() : startListening();
}

async function startListening() {
    if (usingLibrary && agent) {
        // Request mic permission once from JS (must be in user-gesture context).
        if (!micPermissionGranted) {
            try {
                const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                stream.getTracks().forEach(t => t.stop());
                micPermissionGranted = true;
                addLog('Mic permission granted', 'info');
            } catch (e) {
                addLog(`getUserMedia: ${e.message} (proceeding with Speech API)`, 'info');
                micPermissionGranted = true; // Don't ask again regardless
            }
        }
        agent.start();
        listening = true;
        micBtn.classList.add('listening');
        statusEl.textContent = 'Listening...';
        statusEl.className = 'status-listening';
        addLog('Recognition started (VoiceAgentJs)', 'info');
        return;
    }

    // Fallback: Web Speech API
    if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {
        addLog('Speech recognition not supported', 'error');
        statusEl.textContent = 'Not supported';
        return;
    }

    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!recognition) {
        recognition = new SR();
        recognition.interimResults = false;
        recognition.maxAlternatives = 1;
        recognition.onresult = (e) => {
            const text = e.results[e.results.length - 1][0].transcript.trim();
            transcriptEl.textContent = text;
            addLog(`Transcript: "${text}"`, 'info');
            const m = matchIntent(text);
            if (m) {
                addLog(`Intent: ${m.intent} [${m.scope}] (${m.confidence.toFixed(2)})`, 'intent');
                handleFallback(m);
            } else {
                addLog(`Unmatched: "${text}"`, 'error');
            }
        };
        recognition.onerror = (e) => {
            addLog(`Error: ${e.error}`, 'error');
            if (e.error !== 'no-speech' && e.error !== 'aborted') stopListening();
        };
        recognition.onend = () => {
            if (listening && config.continuous) recognition.start();
            else stopListening();
        };
    }
    recognition.lang = config.language;
    recognition.continuous = config.continuous;
    recognition.start();
    listening = true;
    micBtn.classList.add('listening');
    statusEl.textContent = 'Listening...';
    statusEl.className = 'status-listening';
    addLog('Recognition started (fallback)', 'info');
}

function stopListening() {
    if (usingLibrary && agent) agent.stop();
    else if (recognition) { listening = false; recognition.stop(); }
    listening = false;
    micBtn.classList.remove('listening');
    statusEl.textContent = 'Tap the mic to start';
    statusEl.className = 'status-idle';
}

// ═══════════════════════════════════════════════════════════════════
// Fallback intent matching (only used when library not loaded)
// ═══════════════════════════════════════════════════════════════════

function matchIntent(text) {
    const norm = text.toLowerCase().trim();
    let best = null, bestScore = 0;
    for (const intent of intents) {
        for (const pat of intent.patterns) {
            const score = matchPattern(norm, pat.toLowerCase());
            if (score > bestScore) {
                bestScore = score;
                best = { intent: intent.name, scope: intent.scope, rawText: text,
                    extractedText: extractWildcard(norm, pat.toLowerCase()), confidence: score };
            }
        }
    }
    const min = config.fuzzyThreshold > 0 ? config.fuzzyThreshold : 0.8;
    return best && best.confidence >= min ? best : null;
}

function matchPattern(text, pattern) {
    if (pattern.includes('*')) {
        const parts = pattern.split('*');
        const re = new RegExp('^' + parts.map(p => escapeRe(p.trim())).join('(.+)') + '$');
        if (re.test(text)) return 1.0;
        if (config.fuzzyThreshold > 0) return dice(text, pattern);
        return 0;
    }
    return dice(text, pattern);
}

function extractWildcard(text, pattern) {
    if (!pattern.includes('*')) return text;
    const parts = pattern.split('*').map(p => p.trim()).filter(Boolean);
    let r = text;
    for (const p of parts) r = r.replace(p, '').trim();
    return r || text;
}

function dice(a, b) {
    const ta = new Set(a.split(/\s+/));
    const tb = new Set(b.replace(/\*/g, ' ').split(/\s+/).filter(Boolean));
    if (tb.size === 0) return 0;
    let inter = 0;
    for (const t of ta) if (tb.has(t)) inter++;
    return (2 * inter) / (ta.size + tb.size);
}

function escapeRe(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }

// Fallback action handler (when library not loaded)
async function handleFallback(r) {
    if (r.scope === 'LOCAL') {
        if (r.intent === 'todo.add' && r.extractedText) {
            todos.push(r.extractedText); addLog(`[LOCAL] Added "${r.extractedText}"`, 'intent'); renderTodos();
        } else if (r.intent === 'todo.remove' && r.extractedText) {
            const i = todos.findIndex(t => t.toLowerCase().includes(r.extractedText.toLowerCase()));
            if (i >= 0) { todos.splice(i, 1); addLog(`[LOCAL] Removed`, 'intent'); renderTodos(); }
        } else if (r.intent === 'todo.list') {
            addLog(todos.length ? `[LOCAL] Tasks: ${todos.join(', ')}` : '[LOCAL] Empty', 'intent');
        }
    } else if (r.scope === 'MCP') {
        let url = config.mcpServerUrl;
        if (!url) { addLog('[MCP] No URL configured', 'error'); return; }
        if (!url.startsWith('http')) url = 'http://' + url;
        try {
            const resp = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ jsonrpc: '2.0', id: Date.now(), method: 'tools/call',
                    params: { name: 'create_task', arguments: { text: r.extractedText } } }) });
            const json = await resp.json();
            addLog(`[MCP] ${json.result?.content?.[0]?.text || 'OK'}`, 'intent');
        } catch (e) { addLog(`[MCP] Failed: ${e.message}`, 'error'); }
    } else if (r.scope === 'REMOTE') {
        let url = config.webhookUrl;
        if (!url) { addLog('[REMOTE] No URL configured', 'error'); return; }
        if (!url.startsWith('http')) url = 'http://' + url;
        try {
            const resp = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ intent: r.intent, extractedText: r.extractedText, rawText: r.rawText }) });
            const json = await resp.json();
            addLog(`[REMOTE] ${json.message || 'Delivered'}`, 'intent');
        } catch (e) { addLog(`[REMOTE] Failed: ${e.message}`, 'error'); }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Todo list rendering
// ═══════════════════════════════════════════════════════════════════

function renderTodos() {
    if (todos.length === 0) {
        todoListEl.innerHTML = '<div class="empty-state">Say "add project status update" to get started</div>';
        return;
    }
    todoListEl.innerHTML = todos.map((t, i) =>
        `<div class="todo-item">
            <span><span class="badge badge-local">LOCAL</span> ${t}</span>
            <button class="todo-remove" onclick="removeTodo(${i})">✕</button>
        </div>`
    ).join('');
}

function removeTodo(i) {
    if (i >= 0 && i < todos.length) {
        todos.splice(i, 1);
        addLog(`[LOCAL] Task removed`, 'intent');
        renderTodos();
    }
}

// ═══════════════════════════════════════════════════════════════════
// Settings handlers
// ═══════════════════════════════════════════════════════════════════

function updateLang() {
    config.language = langSelect.value;
    if (usingLibrary && agent) agent.updateLanguage(config.language);
    addLog(`Language → ${config.language}`, 'info');
}

function updateContinuous() {
    config.continuous = continuousCheck.checked;
    if (usingLibrary && agent) agent.setContinuous(config.continuous);
    addLog(`Continuous → ${config.continuous}`, 'info');
}

function updateFuzzy() {
    config.fuzzyThreshold = parseFloat(fuzzySlider.value);
    fuzzyValue.textContent = config.fuzzyThreshold.toFixed(1);
    if (usingLibrary && agent) agent.setFuzzyThreshold(config.fuzzyThreshold);
}

function updateMcpUrl() {
    config.mcpServerUrl = mcpUrlInput.value;
    if (config.mcpServerUrl && usingLibrary && agent) {
        let url = config.mcpServerUrl;
        if (!url.startsWith('http')) url = 'http://' + url;
        // Register MCP action with the library's built-in Ktor-based McpClient.
        // This replaces any previously registered handler for 'task.create'.
        agent.registerMcpAction(
            'task.create', 'en',
            ['create task *', 'new task *', 'task *'],
            url, 'create_task'
        );
        addLog(`[MCP] Registered via library McpClient → ${url}`, 'info');
    }
}

function updateWebhookUrl() {
    config.webhookUrl = webhookUrlInput.value;
    if (config.webhookUrl && usingLibrary && agent) {
        let url = config.webhookUrl;
        if (!url.startsWith('http')) url = 'http://' + url;
        // Register webhook action with the library's built-in Ktor-based WebhookActionHandler.
        agent.registerWebhookAction(
            'notify.team', 'en',
            ['notify *', 'send notification *', 'alert *'],
            url
        );
        addLog(`[REMOTE] Registered via library WebhookActionHandler → ${url}`, 'info');
    }
}
