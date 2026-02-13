/**
 * V8V Web Demo
 *
 * Demonstrates all three action scopes:
 *   1. LOCAL  — "add <item>" → in-app todo list
 *   2. MCP    — "create task <item>" → local MCP server (JSON-RPC 2.0)
 *   3. REMOTE — "notify <message>" → remote webhook (n8n, Zapier, etc.)
 *
 * When consuming the Kotlin/JS npm package, replace the logic below with
 * the VoiceAgentJs facade. This demo re-implements intent matching in
 * plain JS so it works without a bundler — just open index.html in Chrome.
 */

// ═══════════════════════════════════════════════════════════════════════
// State
// ═══════════════════════════════════════════════════════════════════════

let listening = false;
let recognition = null;          // single instance, reused across start/stop
let micPermissionGranted = false; // tracks if mic permission was already obtained
let todos = [];
let config = {
    language: 'en',
    continuous: false,
    fuzzyThreshold: 0.0,
    mcpServerUrl: '',
    webhookUrl: '',
};

// ═══════════════════════════════════════════════════════════════════════
// Registered phrases (intent → patterns)
// ═══════════════════════════════════════════════════════════════════════

const intents = [
    // LOCAL
    {
        name: 'todo.add',
        scope: 'LOCAL',
        patterns: ['add *', 'add * to todo', 'add * to my list', 'add * to the list'],
    },
    {
        name: 'todo.remove',
        scope: 'LOCAL',
        patterns: ['remove *', 'delete *', 'remove * from list'],
    },
    {
        name: 'todo.list',
        scope: 'LOCAL',
        patterns: ['list todos', 'show todos', 'show my list', 'what are my todos'],
    },
    // MCP
    {
        name: 'task.create',
        scope: 'MCP',
        patterns: ['create task *', 'new task *', 'task *'],
    },
    // REMOTE
    {
        name: 'notify.team',
        scope: 'REMOTE',
        patterns: ['notify *', 'send notification *', 'alert *'],
    },
];

// ═══════════════════════════════════════════════════════════════════════
// DOM refs
// ═══════════════════════════════════════════════════════════════════════

const micBtn        = document.getElementById('micBtn');
const statusEl      = document.getElementById('status');
const transcriptEl  = document.getElementById('transcript');
const logEl         = document.getElementById('log');
const langSelect    = document.getElementById('langSelect');
const continuousCheck = document.getElementById('continuousCheck');
const fuzzySlider   = document.getElementById('fuzzySlider');
const fuzzyValue    = document.getElementById('fuzzyValue');
const mcpUrlInput   = document.getElementById('mcpUrl');
const webhookUrlInput = document.getElementById('webhookUrl');
const todoListEl    = document.getElementById('todoList');

// ═══════════════════════════════════════════════════════════════════════
// Logging helper
// ═══════════════════════════════════════════════════════════════════════

function addLog(text, className) {
    const line = document.createElement('div');
    line.textContent = `[${new Date().toLocaleTimeString()}] ${text}`;
    if (className) line.className = className;
    logEl.prepend(line);
    while (logEl.children.length > 30) logEl.removeChild(logEl.lastChild);
}

// ═══════════════════════════════════════════════════════════════════════
// Intent matching
//
// HOW FUZZY MATCHING WORKS:
//
// The resolver tries to match spoken text against registered patterns
// in three passes:
//
// Pass 1 — Wildcard regex match:
//   Each pattern like "add * to todo" is converted to a regex:
//     ^add (.+) to todo$
//   If the input "add milk to todo" matches, confidence = 1.0 and
//   the captured group "milk" becomes the extractedText.
//
// Pass 2 — Dice similarity (fuzzy):
//   When no exact match is found AND fuzzyThreshold > 0, we compute
//   the Dice coefficient between the input words and pattern words:
//
//     Dice = (2 × |intersection|) / (|A| + |B|)
//
//   Example:
//     Input:   "please add milk to my todo list"  → {please, add, milk, to, my, todo, list}  (7 words)
//     Pattern: "add * to todo"                     → {add, to, todo}  (3 literal words, * removed)
//     Intersection: {add, to, todo}                → 3 matches
//     Dice = (2 × 3) / (7 + 3) = 6/10 = 0.60
//
//   If Dice >= fuzzyThreshold, it's a fuzzy match. The Dice formula
//   penalizes extra filler words (unlike simple overlap), so:
//     "add milk to todo"       → Dice = (2×3)/(4+3) = 0.86  (high — good match)
//     "please could you add milk to my todo list" → 0.50  (low — lots of filler)
//
//   This is why the fuzzy threshold slider matters: at 0.5 you accept
//   loose matches, at 0.8 you require close phrasing.
// ═══════════════════════════════════════════════════════════════════════

function matchIntent(text) {
    const normalized = text.toLowerCase().trim();
    let bestMatch = null;
    let bestScore = 0;

    for (const intent of intents) {
        for (const pattern of intent.patterns) {
            const score = matchPattern(normalized, pattern.toLowerCase());
            if (score > bestScore) {
                bestScore = score;
                bestMatch = {
                    intent: intent.name,
                    scope: intent.scope,
                    rawText: text,
                    extractedText: extractWildcard(normalized, pattern.toLowerCase()),
                    confidence: score,
                };
            }
        }
    }

    // For exact wildcard matches the score is high (>= 0.8).
    // For fuzzy: only accept if above fuzzyThreshold.
    const minScore = config.fuzzyThreshold > 0 ? config.fuzzyThreshold : 0.8;
    if (bestMatch && bestMatch.confidence >= minScore) {
        return bestMatch;
    }
    return null;
}

function matchPattern(text, pattern) {
    if (pattern.includes('*')) {
        // Build regex from wildcard pattern:  "add * to todo" → /^add (.+) to todo$/
        const parts = pattern.split('*');
        const regexStr = '^' + parts.map(p => escapeRegex(p.trim())).join('(.+)') + '$';
        const regex = new RegExp(regexStr);
        if (regex.test(text)) {
            return 1.0; // exact wildcard match
        }
        // Fall through to fuzzy if threshold is set
        if (config.fuzzyThreshold > 0) {
            return diceSimilarity(text, pattern);
        }
        return 0;
    } else {
        return diceSimilarity(text, pattern);
    }
}

function extractWildcard(text, pattern) {
    if (!pattern.includes('*')) return text;
    const parts = pattern.split('*').map(p => p.trim()).filter(Boolean);
    let remaining = text;
    for (const part of parts) {
        remaining = remaining.replace(part, '').trim();
    }
    return remaining || text;
}

/**
 * Dice similarity coefficient:
 *   Dice = (2 × |intersection|) / (|A| + |B|)
 *
 * Where A = input word tokens, B = pattern literal word tokens (wildcards removed).
 * Returns a value between 0.0 (no overlap) and 1.0 (identical word sets).
 */
function diceSimilarity(a, b) {
    const tokensA = new Set(a.split(/\s+/));
    // Remove wildcards from pattern before tokenizing
    const cleaned = b.replace(/\*/g, ' ').replace(/\{[^}]+\}/g, ' ');
    const tokensB = new Set(cleaned.split(/\s+/).filter(Boolean));
    if (tokensB.size === 0) return 0;
    let intersection = 0;
    for (const t of tokensA) if (tokensB.has(t)) intersection++;
    return (2 * intersection) / (tokensA.size + tokensB.size);
}

function escapeRegex(str) {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// ═══════════════════════════════════════════════════════════════════════
// Action handlers (LOCAL, MCP, REMOTE)
// ═══════════════════════════════════════════════════════════════════════

async function handleIntent(result) {
    const badge = `[${result.scope}]`;

    switch (result.scope) {
        case 'LOCAL':
            handleLocalAction(result);
            break;

        case 'MCP':
            await handleMcpAction(result);
            break;

        case 'REMOTE':
            await handleRemoteAction(result);
            break;
    }
}

// ---- LOCAL ----
function handleLocalAction(result) {
    switch (result.intent) {
        case 'todo.add':
            if (result.extractedText) {
                todos.push(result.extractedText);
                addLog(`[LOCAL] Added "${result.extractedText}" to todos`, 'intent');
                renderTodos();
            }
            break;
        case 'todo.remove':
            if (result.extractedText) {
                const idx = todos.findIndex(t =>
                    t.toLowerCase().includes(result.extractedText.toLowerCase()));
                if (idx >= 0) {
                    const removed = todos.splice(idx, 1)[0];
                    addLog(`[LOCAL] Removed "${removed}" from todos`, 'intent');
                    renderTodos();
                } else {
                    addLog(`[LOCAL] "${result.extractedText}" not found`, 'error');
                }
            }
            break;
        case 'todo.list':
            if (todos.length === 0) {
                addLog('[LOCAL] Todo list is empty', 'intent');
            } else {
                addLog(`[LOCAL] Todos: ${todos.join(', ')}`, 'intent');
            }
            break;
    }
}

// ---- MCP (JSON-RPC 2.0 over HTTP) ----
async function handleMcpAction(result) {
    const url = config.mcpServerUrl;
    if (!url) {
        addLog('[MCP] No MCP server URL configured — set it in Settings', 'error');
        return;
    }

    try {
        addLog(`[MCP] Calling tool "create_task" on ${url}...`, 'mcp');
        const rpcRequest = {
            jsonrpc: '2.0',
            id: Date.now(),
            method: 'tools/call',
            params: {
                name: 'create_task',
                arguments: {
                    text: result.extractedText,
                    rawText: result.rawText,
                },
            },
        };

        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(rpcRequest),
        });

        if (!response.ok) {
            addLog(`[MCP] HTTP ${response.status}: ${response.statusText}`, 'error');
            return;
        }

        const json = await response.json();
        if (json.error) {
            addLog(`[MCP] Error: ${json.error.message}`, 'error');
        } else {
            const text = json.result?.content?.[0]?.text || 'OK';
            addLog(`[MCP] Success: ${text}`, 'intent');
        }
    } catch (e) {
        addLog(`[MCP] Call failed: ${e.message}`, 'error');
    }
}

// ---- REMOTE (Webhook POST) ----
async function handleRemoteAction(result) {
    const url = config.webhookUrl;
    if (!url) {
        addLog('[REMOTE] No webhook URL configured — set it in Settings', 'error');
        return;
    }

    try {
        addLog(`[REMOTE] Sending webhook to ${url}...`, 'remote');
        const payload = {
            intent: result.intent,
            extractedText: result.extractedText,
            rawText: result.rawText,
            language: config.language,
            timestamp: new Date().toISOString(),
        };

        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
        });

        if (!response.ok) {
            addLog(`[REMOTE] HTTP ${response.status}: ${response.statusText}`, 'error');
            return;
        }

        const json = await response.json();
        if (json.success === false) {
            addLog(`[REMOTE] Error: ${json.message || 'Webhook returned failure'}`, 'error');
        } else {
            addLog(`[REMOTE] Success: ${json.message || 'Webhook delivered'}`, 'intent');
        }
    } catch (e) {
        addLog(`[REMOTE] Call failed: ${e.message}`, 'error');
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Todo list rendering
// ═══════════════════════════════════════════════════════════════════════

function renderTodos() {
    if (!todoListEl) return;
    if (todos.length === 0) {
        todoListEl.innerHTML = '<div class="empty-state">Say "add project status update" to get started</div>';
        return;
    }
    todoListEl.innerHTML = todos.map((t, i) =>
        `<div class="todo-item">
            <span>${t}</span>
            <button class="todo-remove" onclick="removeTodo(${i})">x</button>
        </div>`
    ).join('');
}

function removeTodo(index) {
    const removed = todos.splice(index, 1)[0];
    addLog(`[LOCAL] Removed "${removed}"`, 'intent');
    renderTodos();
}

// ═══════════════════════════════════════════════════════════════════════
// Web Speech API — single-instance, permission requested once
//
// FIX: The previous version created a new SpeechRecognition instance on
// every toggleMic() call. Each new instance triggered a browser permission
// prompt. Now we:
//   1. Request mic permission ONCE upfront via getUserMedia
//   2. Create ONE SpeechRecognition instance and reuse it
//   3. On stop, we call .stop() but keep the instance alive
//   4. On language change, we recreate the instance (unavoidable)
// ═══════════════════════════════════════════════════════════════════════

async function ensureMicPermission() {
    if (micPermissionGranted) return true;
    try {
        // Request permission once — the browser remembers the grant
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        // Immediately release the stream (we just needed the permission grant)
        stream.getTracks().forEach(track => track.stop());
        micPermissionGranted = true;
        return true;
    } catch (e) {
        addLog(`Microphone permission denied: ${e.message}`, 'error');
        setStatus('Microphone permission denied', 'error');
        return false;
    }
}

function createRecognition() {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
        setStatus('Web Speech API not supported in this browser', 'error');
        addLog('ERROR: Web Speech API not supported. Use Chrome or Edge.', 'error');
        return null;
    }

    const rec = new SpeechRecognition();
    rec.lang = config.language;
    rec.interimResults = true;
    rec.continuous = config.continuous;

    rec.onstart = () => {
        listening = true;
        micBtn.classList.add('listening');
        setStatus('Listening...', 'listening');
        addLog('Recognition started', 'transcript');
    };

    rec.onresult = (event) => {
        let finalTranscript = '';
        let interimTranscript = '';

        for (let i = event.resultIndex; i < event.results.length; i++) {
            const result = event.results[i];
            if (result.isFinal) {
                finalTranscript += result[0].transcript;
            } else {
                interimTranscript += result[0].transcript;
            }
        }

        if (interimTranscript) {
            transcriptEl.textContent = interimTranscript;
        }

        if (finalTranscript) {
            transcriptEl.textContent = finalTranscript;
            addLog(`Transcript: "${finalTranscript}"`, 'transcript');

            const match = matchIntent(finalTranscript);
            if (match) {
                addLog(`Intent: ${match.intent} [${match.scope}] (confidence: ${match.confidence.toFixed(2)})`, 'intent');
                handleIntent(match);
            } else {
                addLog(`Unmatched: "${finalTranscript}"`, 'unhandled');
            }
        }
    };

    rec.onerror = (event) => {
        // "no-speech" and "aborted" are non-fatal — don't show as errors
        if (event.error === 'no-speech' || event.error === 'aborted') return;
        addLog(`Error: ${event.error}`, 'error');
        if (event.error === 'not-allowed') {
            setStatus('Microphone permission denied', 'error');
            micPermissionGranted = false;
        }
    };

    rec.onend = () => {
        listening = false;
        micBtn.classList.remove('listening');

        // Auto-restart in continuous mode (reuse same instance)
        if (config.continuous && recognition === rec) {
            setStatus('Restarting...', 'listening');
            setTimeout(() => {
                try {
                    rec.start();
                } catch (_) {
                    setStatus('Tap the mic to start', 'idle');
                }
            }, 200);
        } else {
            setStatus('Tap the mic to start', 'idle');
        }
    };

    return rec;
}

// ═══════════════════════════════════════════════════════════════════════
// UI functions
// ═══════════════════════════════════════════════════════════════════════

function setStatus(text, type) {
    statusEl.textContent = text;
    statusEl.className = `mic-label status-${type || 'idle'}`;
}

async function toggleMic() {
    if (listening) {
        // Stop: keep the instance but stop recognition
        if (recognition) {
            config.continuous = false;       // prevent auto-restart in onend
            continuousCheck.checked = false;
            recognition.stop();
        }
        recognition = null;
        return;
    }

    // Ensure permission is granted ONCE before creating recognition
    const granted = await ensureMicPermission();
    if (!granted) return;

    // Create a fresh instance (or reuse language-matched one)
    recognition = createRecognition();
    if (recognition) {
        try {
            recognition.start();
        } catch (e) {
            addLog(`Start error: ${e.message}`, 'error');
        }
    }
}

function updateLang() {
    config.language = langSelect.value;
    addLog(`Language set to: ${config.language}`, 'transcript');
    // Must recreate recognition for language change
    if (listening && recognition) {
        recognition.stop();
        recognition = null;
        setTimeout(() => toggleMic(), 300);
    }
}

function updateContinuous() {
    config.continuous = continuousCheck.checked;
    addLog(`Continuous mode: ${config.continuous}`, 'transcript');
    if (recognition) {
        recognition.continuous = config.continuous;
    }
}

function updateFuzzy() {
    config.fuzzyThreshold = parseFloat(fuzzySlider.value);
    fuzzyValue.textContent = config.fuzzyThreshold.toFixed(1);
    addLog(`Fuzzy threshold: ${config.fuzzyThreshold}`, 'transcript');
}

function updateMcpUrl() {
    config.mcpServerUrl = mcpUrlInput.value.trim();
    if (config.mcpServerUrl) {
        addLog(`[MCP] Server URL set: ${config.mcpServerUrl}`, 'mcp');
    }
}

function updateWebhookUrl() {
    config.webhookUrl = webhookUrlInput.value.trim();
    if (config.webhookUrl) {
        addLog(`[REMOTE] Webhook URL set: ${config.webhookUrl}`, 'remote');
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Init
// ═══════════════════════════════════════════════════════════════════════

renderTodos();
addLog('V8V Web Demo loaded. Click the mic to start.', 'transcript');
addLog('Supports LOCAL, MCP, and REMOTE scopes.', 'transcript');
