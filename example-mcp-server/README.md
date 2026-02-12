# MCP Task Manager (App B)

This is the **second app** in the MCP cross-app voice demo.

## The Two-App Setup

```
┌─────────────────────────────┐        HTTP (JSON-RPC)       ┌─────────────────────────────┐
│  App A — V8V                │  ──────────────────────────► │  App B — Task Manager       │
│  (example-web or Android)   │       POST /mcp              │  (this server)              │
│                             │                              │                             │
│  User says:                 │                              │  📋 Dashboard shows tasks   │
│  "create task buy milk"     │                              │  ✅ buy milk                │
│                             │  ◄────────────────────────── │  ✅ fix the bug             │
│  → Transcribes              │     Success response         │  ✅ call dentist            │
│  → Matches intent           │                              │                             │
│  → McpActionHandler sends   │                              │  http://localhost:3001      │
│    request to App B         │                              │  (open in browser)          │
└─────────────────────────────┘                              └─────────────────────────────┘
```

**App A** = Your V8V voice agent (any platform: web, Android, JVM, iOS, macOS).
It listens for speech, resolves intents, and sends MCP requests.

**App B** = This server. A separate app with its own task list and a web dashboard.
When App A sends a voice command, App B executes it and you see the result.

## Quick Start

### Step 1: Start App B (this server)

```bash
node server.js
```

Then open **http://localhost:3001** in your browser — you'll see the dashboard.

### Step 2: Start App A (V8V)

**Option A — Web example:**
1. Open `example-web/index.html` in another browser tab
2. In Settings, set MCP URL to `http://localhost:3001/mcp`
3. Click the mic and say **"create task buy groceries"**
4. Switch to the App B tab — you'll see the task appear!

**Option B — JVM CLI:**
```bash
./gradlew :example-jvm:run
```
Then type: `create task buy groceries`

**Option C — Android:**
1. The Android example uses the embedded mock server by default
2. To use this server instead, change `MCP_PORT` in `MainViewModel.kt`
3. Ensure `network_security_config.xml` allows cleartext to your IP

### Step 3: Watch the cross-app magic

- Say commands in App A → tasks appear in App B's dashboard
- The dashboard polls every 800ms and shows new tasks with animation
- The event log shows each MCP call as it arrives

## MCP Tools Exposed

| Tool | Description | Example voice command |
|------|------------|----------------------|
| `create_task` | Create a task | "create task buy groceries" |
| `list_tasks` | List all tasks | (via curl or code) |
| `complete_task` | Mark a task done | (via curl or code) |
| `delete_task` | Delete a task | (via curl or code) |
| `search_tasks` | Search tasks | (via curl or code) |

## Testing with curl

```bash
# Initialize MCP session
curl -s -X POST http://localhost:3001/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'

# Create a task (as if V8V said "create task buy milk")
curl -s -X POST http://localhost:3001/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"create_task","arguments":{"text":"buy milk"}}}'

# List tasks
curl -s -X POST http://localhost:3001/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_tasks","arguments":{}}}'
```

## What This Demonstrates

This proves that MCP enables **cross-app voice control**:
- App A doesn't know anything about App B's internals
- App A just calls a "tool" named `create_task` via the MCP protocol
- App B handles the tool call however it wants (store in DB, update UI, etc.)
- In production, App B could be Notion, Todoist, Jira, Slack, or any MCP-compatible app
