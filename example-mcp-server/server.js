/**
 * MCP Task Manager — "App B" for V8V MCP Testing
 *
 * This is the SECOND app in the MCP demo. It represents a standalone
 * task manager application that exposes MCP tools for cross-app control.
 *
 * Architecture:
 *   ┌──────────────────────┐       JSON-RPC 2.0        ┌──────────────────────┐
 *   │  App A               │  ────────────────────────► │  App B (this server) │
 *   │  (V8V)               │       POST /mcp            │  (Task Manager)      │
 *   │                      │                            │                      │
 *   │  "create task milk"  │                            │  📋 Task list UI     │
 *   │  → McpActionHandler  │                            │  ✅ milk             │
 *   │    sends request     │  ◄──────────────────────── │  ✅ groceries        │
 *   │                      │       Success response     │                      │
 *   └──────────────────────┘                            └──────────────────────┘
 *                                                            │
 *                                                       http://localhost:3001
 *                                                       (web dashboard)
 *
 * It serves TWO things:
 *   - GET  /         → Web dashboard (see tasks appear in real-time)
 *   - POST /mcp      → MCP JSON-RPC endpoint (called by V8V)
 *   - GET  /api/tasks → REST API to poll tasks (used by dashboard)
 *
 * Usage:
 *   node server.js                    # starts on port 3001
 *   node server.js --port 4000        # custom port
 *   node server.js --cors             # enable CORS (required for web App A)
 *
 * Testing flow:
 *   1. Start this server:  node server.js --cors
 *   2. Open http://localhost:3001 in a browser (App B dashboard)
 *   3. Open example-web/index.html in another tab (App A)
 *   4. In App A settings, set MCP URL to http://localhost:3001/mcp
 *   5. Say "create task buy groceries" in App A
 *   6. Watch the task appear in App B's dashboard!
 */

const http = require('http');
const path = require('path');

// ── Config ──────────────────────────────────────────────────────────

const args = process.argv.slice(2);
const PORT = getArg('--port', 3001);
const CORS = args.includes('--cors');

function getArg(name, defaultValue) {
    const idx = args.indexOf(name);
    return idx >= 0 && args[idx + 1] ? parseInt(args[idx + 1], 10) : defaultValue;
}

// ── In-memory task store ────────────────────────────────────────────

const tasks = [];

// ── Tool definitions (MCP schema) ───────────────────────────────────

const TOOLS = [
    {
        name: 'create_task',
        description: 'Create a new task. Returns the created task with its index.',
        inputSchema: {
            type: 'object',
            properties: {
                text: { type: 'string', description: 'Task description' },
                rawText: { type: 'string', description: 'Original spoken text (optional)' },
            },
            required: ['text'],
        },
    },
    {
        name: 'list_tasks',
        description: 'List all tasks currently stored.',
        inputSchema: { type: 'object', properties: {} },
    },
    {
        name: 'complete_task',
        description: 'Mark a task as completed by index.',
        inputSchema: {
            type: 'object',
            properties: { index: { type: 'number', description: 'Task index (0-based)' } },
            required: ['index'],
        },
    },
    {
        name: 'delete_task',
        description: 'Delete a task by its index (0-based).',
        inputSchema: {
            type: 'object',
            properties: { index: { type: 'number', description: 'Task index to delete' } },
            required: ['index'],
        },
    },
    {
        name: 'search_tasks',
        description: 'Search tasks by keyword.',
        inputSchema: {
            type: 'object',
            properties: { query: { type: 'string', description: 'Search query' } },
            required: ['query'],
        },
    },
];

// ── Tool handlers ───────────────────────────────────────────────────

function handleToolCall(name, toolArgs) {
    switch (name) {
        case 'create_task': {
            const text = toolArgs.text || 'untitled';
            const task = {
                id: tasks.length,
                text,
                rawText: toolArgs.rawText || '',
                completed: false,
                createdAt: new Date().toISOString(),
                source: 'mcp',
            };
            tasks.push(task);
            log(`  [create_task] Created: "${text}" (total: ${tasks.length})`);
            return {
                content: [{ type: 'text', text: `Task created: "${text}" (id: ${task.id}, total: ${tasks.length})` }],
                isError: false,
            };
        }

        case 'list_tasks': {
            if (tasks.length === 0) {
                return { content: [{ type: 'text', text: 'No tasks found.' }], isError: false };
            }
            const list = tasks.map((t, i) => {
                const status = t.completed ? '✅' : '⬜';
                return `${i}: ${status} ${t.text} (${t.createdAt})`;
            }).join('\n');
            return { content: [{ type: 'text', text: `Tasks:\n${list}` }], isError: false };
        }

        case 'complete_task': {
            const index = parseInt(toolArgs.index, 10);
            if (isNaN(index) || index < 0 || index >= tasks.length) {
                return { content: [{ type: 'text', text: `Invalid index: ${toolArgs.index}` }], isError: true };
            }
            tasks[index].completed = true;
            log(`  [complete_task] Completed: "${tasks[index].text}"`);
            return { content: [{ type: 'text', text: `Completed: "${tasks[index].text}"` }], isError: false };
        }

        case 'delete_task': {
            const index = parseInt(toolArgs.index, 10);
            if (isNaN(index) || index < 0 || index >= tasks.length) {
                return { content: [{ type: 'text', text: `Invalid index: ${toolArgs.index}` }], isError: true };
            }
            const removed = tasks.splice(index, 1)[0];
            log(`  [delete_task] Deleted: "${removed.text}"`);
            return { content: [{ type: 'text', text: `Deleted: "${removed.text}" (remaining: ${tasks.length})` }], isError: false };
        }

        case 'search_tasks': {
            const query = (toolArgs.query || '').toLowerCase();
            const matches = tasks.filter(t => t.text.toLowerCase().includes(query));
            if (matches.length === 0) {
                return { content: [{ type: 'text', text: `No tasks matching "${toolArgs.query}"` }], isError: false };
            }
            const list = matches.map(t => `${t.id}: ${t.text}`).join('\n');
            return { content: [{ type: 'text', text: `Found ${matches.length} task(s):\n${list}` }], isError: false };
        }

        default:
            return { content: [{ type: 'text', text: `Unknown tool: ${name}` }], isError: true };
    }
}

// ── JSON-RPC 2.0 method dispatch ────────────────────────────────────

function handleMethod(id, method, params) {
    switch (method) {
        case 'initialize':
            return jsonRpcSuccess(id, {
                protocolVersion: '2024-11-05',
                capabilities: { tools: { listChanged: false } },
                serverInfo: { name: 'mcp-task-manager', version: '1.0.0' },
            });
        case 'tools/list':
            return jsonRpcSuccess(id, { tools: TOOLS });
        case 'tools/call': {
            const result = handleToolCall(params?.name, params?.arguments || {});
            return jsonRpcSuccess(id, result);
        }
        case 'notifications/initialized':
            return null;
        default:
            return jsonRpcError(id, -32601, `Method not found: ${method}`);
    }
}

function jsonRpcSuccess(id, result) {
    return JSON.stringify({ jsonrpc: '2.0', id, result });
}
function jsonRpcError(id, code, message) {
    return JSON.stringify({ jsonrpc: '2.0', id, error: { code, message } });
}

// ── Web Dashboard HTML ──────────────────────────────────────────────

function dashboardHtml() {
    return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>MCP Task Manager — App B</title>
<style>
  :root { --bg:#0f0f0f; --surface:#1a1a2e; --border:#2a2a4a; --primary:#e67e22;
          --success:#2ecc71; --text:#e0e0e0; --muted:#8888a0; --error:#e74c3c; }
  * { margin:0; padding:0; box-sizing:border-box; }
  body { font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
         background:var(--bg); color:var(--text); min-height:100vh;
         display:flex; flex-direction:column; align-items:center; padding:2rem 1rem; }
  h1 { font-size:1.8rem; margin-bottom:0.25rem; }
  .subtitle { color:var(--muted); font-size:0.9rem; margin-bottom:0.5rem; }
  .badge { display:inline-block; background:rgba(230,126,34,0.2); color:var(--primary);
           font-size:0.7rem; font-weight:700; padding:2px 8px; border-radius:4px;
           text-transform:uppercase; margin-bottom:1.5rem; }
  .container { width:100%; max-width:560px; display:flex; flex-direction:column; gap:1rem; }
  .card { background:var(--surface); border:1px solid var(--border);
          border-radius:12px; padding:1.25rem; }
  .card-title { font-size:0.85rem; font-weight:600; text-transform:uppercase;
                letter-spacing:0.05em; color:var(--muted); margin-bottom:0.75rem; }

  .status { display:flex; align-items:center; gap:0.5rem; }
  .dot { width:10px; height:10px; border-radius:50%; background:var(--success);
         animation:blink 2s ease-in-out infinite; }
  @keyframes blink { 0%,100%{opacity:1;} 50%{opacity:0.3;} }
  .status-text { font-size:0.85rem; color:var(--muted); }

  .task-list { display:flex; flex-direction:column; gap:0.5rem; }
  .task-item { display:flex; align-items:center; gap:0.75rem; padding:0.6rem 0.75rem;
               background:rgba(255,255,255,0.03); border-radius:8px;
               border:1px solid var(--border); animation:slideIn 0.3s ease-out; }
  @keyframes slideIn { from{opacity:0;transform:translateY(-10px);} to{opacity:1;transform:translateY(0);} }
  .task-item.completed { opacity:0.5; }
  .task-item.completed .task-text { text-decoration:line-through; }
  .task-check { font-size:1.1rem; }
  .task-text { flex:1; font-size:0.9rem; }
  .task-source { font-size:0.65rem; font-weight:700; padding:2px 6px; border-radius:4px;
                 background:rgba(230,126,34,0.15); color:var(--primary); }
  .task-time { font-size:0.7rem; color:var(--muted); }
  .empty { color:var(--muted); font-style:italic; font-size:0.85rem; text-align:center; padding:1rem; }

  .event-log { font-family:'SF Mono','Fira Code',monospace; font-size:0.75rem;
               max-height:180px; overflow-y:auto; color:var(--muted); line-height:1.6; }
  .event-log .new { color:var(--primary); }
  .event-log .init { color:var(--success); }

  .how-it-works { font-size:0.82rem; color:var(--muted); line-height:1.6; }
  .how-it-works code { background:rgba(230,126,34,0.15); padding:1px 5px;
                       border-radius:3px; font-family:monospace; font-size:0.78rem; }
  .counter { font-size:2.5rem; font-weight:700; text-align:center; color:var(--primary); }
  .counter-label { text-align:center; font-size:0.8rem; color:var(--muted); }
</style>
</head>
<body>
  <h1>MCP Task Manager</h1>
  <p class="subtitle">App B — receives voice commands from App A via MCP</p>
  <div class="badge">MCP SERVER</div>

  <div class="container">
    <div class="card">
      <div class="status">
        <div class="dot"></div>
        <div class="status-text">Listening on <strong>http://localhost:${PORT}/mcp</strong></div>
      </div>
    </div>

    <div class="card">
      <div class="counter" id="taskCount">0</div>
      <div class="counter-label">Tasks created via voice commands</div>
    </div>

    <div class="card">
      <div class="card-title">Task List</div>
      <div class="task-list" id="taskList">
        <div class="empty">Waiting for voice commands from App A...</div>
      </div>
    </div>

    <div class="card">
      <div class="card-title">MCP Event Log</div>
      <div class="event-log" id="eventLog"></div>
    </div>

    <div class="card">
      <div class="card-title">How to Test</div>
      <div class="how-it-works">
        <strong>1.</strong> Keep this page open (App B dashboard).<br>
        <strong>2.</strong> Open <code>example-web/index.html</code> in another tab (App A).<br>
        <strong>3.</strong> In App A settings, set MCP URL to <code>http://localhost:${PORT}/mcp</code><br>
        <strong>4.</strong> Click the mic and say <code>"create task buy groceries"</code><br>
        <strong>5.</strong> Watch the task appear here!<br><br>
        Or use the JVM example: <code>./gradlew :example-jvm:run</code> and type <code>create task buy milk</code>
      </div>
    </div>
  </div>

  <script>
    const taskList = document.getElementById('taskList');
    const taskCount = document.getElementById('taskCount');
    const eventLog = document.getElementById('eventLog');
    let lastCount = 0;

    function addEvent(text, className) {
      const line = document.createElement('div');
      line.textContent = '[' + new Date().toLocaleTimeString() + '] ' + text;
      if (className) line.className = className;
      eventLog.prepend(line);
      while (eventLog.children.length > 30) eventLog.removeChild(eventLog.lastChild);
    }

    async function poll() {
      try {
        const res = await fetch('/api/tasks');
        const data = await res.json();
        taskCount.textContent = data.length;

        if (data.length !== lastCount) {
          if (data.length > lastCount && lastCount > 0) {
            const newest = data[data.length - 1];
            addEvent('New task: "' + newest.text + '" (via ' + newest.source + ')', 'new');
          }
          lastCount = data.length;
          renderTasks(data);
        }
      } catch (e) { /* server might be restarting */ }
    }

    function renderTasks(data) {
      if (data.length === 0) {
        taskList.innerHTML = '<div class="empty">Waiting for voice commands from App A...</div>';
        return;
      }
      taskList.innerHTML = data.map(function(t, i) {
        var cls = 'task-item' + (t.completed ? ' completed' : '');
        return '<div class="' + cls + '">'
          + '<span class="task-check">' + (t.completed ? '\\u2705' : '\\u2B1C') + '</span>'
          + '<span class="task-text">' + escHtml(t.text) + '</span>'
          + '<span class="task-source">MCP</span>'
          + '<span class="task-time">' + new Date(t.createdAt).toLocaleTimeString() + '</span>'
          + '</div>';
      }).join('');
    }

    function escHtml(s) {
      return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
    }

    addEvent('Dashboard connected. Polling for tasks...', 'init');
    setInterval(poll, 800);
    poll();
  </script>
</body>
</html>`;
}

// ── HTTP server ─────────────────────────────────────────────────────

function log(msg) {
    console.log(`[${new Date().toISOString()}] ${msg}`);
}

const server = http.createServer((req, res) => {
    // CORS headers
    if (CORS || true) { // Always enable CORS — needed for web App A
        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    }

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    // Dashboard
    if (req.method === 'GET' && (req.url === '/' || req.url === '/index.html')) {
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(dashboardHtml());
        return;
    }

    // Tasks REST API (for dashboard polling)
    if (req.method === 'GET' && req.url === '/api/tasks') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(tasks));
        return;
    }

    // MCP JSON-RPC endpoint
    if (req.method === 'POST' && req.url === '/mcp') {
        let body = '';
        req.on('data', chunk => { body += chunk; });
        req.on('end', () => {
            try {
                const request = JSON.parse(body);
                const id = request.id || 0;
                const method = request.method || '';
                const params = request.params;

                log(`MCP ${method} (id: ${id})`);

                const response = handleMethod(id, method, params);

                if (response === null) {
                    res.writeHead(204);
                    res.end();
                } else {
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(response);
                }
            } catch (e) {
                log(`Parse error: ${e.message}`);
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(jsonRpcError(0, -32700, `Parse error: ${e.message}`));
            }
        });
        return;
    }

    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Not found' }));
});

server.listen(PORT, () => {
    console.log('');
    console.log('  ╔══════════════════════════════════════════════════╗');
    console.log('  ║  MCP Task Manager (App B)                       ║');
    console.log('  ╠══════════════════════════════════════════════════╣');
    console.log(`  ║  Dashboard:  http://localhost:${PORT}              ║`);
    console.log(`  ║  MCP endpoint: http://localhost:${PORT}/mcp          ║`);
    console.log('  ║                                                  ║');
    console.log('  ║  Tools: create_task, list_tasks, complete_task,  ║');
    console.log('  ║         delete_task, search_tasks                ║');
    console.log('  ╚══════════════════════════════════════════════════╝');
    console.log('');
    console.log('  Open the dashboard in a browser to see tasks appear');
    console.log('  when you speak commands in App A (example-web or');
    console.log('  example-android).');
    console.log('');
    console.log('  Waiting for connections...');
    console.log('');
});
