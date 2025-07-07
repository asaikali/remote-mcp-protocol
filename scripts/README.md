# MCP HTTPie Client Scripts

This folder contains helper scripts for exploring the Model Context Protocol (MCP) “everything” server over the Streamable HTTP transport, using [HTTPie](https://httpie.io).

These scripts are designed to give you **raw HTTP traffic visibility** via HTTPie. The MCP Inspector only shows summaries of JSON-RPC requests, responses, and notifications, whereas these scripts let you see the exact HTTP exchanges.

## Scripts Included

* **`init.sh`** — Bootstraps a new MCP session:

    1. Sends **initialize** request
    2. Extracts the `Mcp-Session-Id` from response headers
    3. Sends **initialized** notification
    4. Opens an SSE listener on the same connection

* **`post.sh`** — Invokes a sequence of tool calls for a given session:

    1. **List tools** (`tools/list`)
    2. **Echo tool** (`tools/call`)
    3. **Long-running operation** (`tools/call` with progress token)

* **`get.sh`** — Opens an additional SSE stream for the same session ID to observe all server-initiated notifications (logs, progress, resource updates, etc.).

## Prerequisites

1. **Clone the MCP servers repository**:

   ```bash
   git clone https://github.com/modelcontextprotocol/servers.git
   cd servers/src/everything
   ```

2. **Install dependencies and start the everything server**:

   ```bash
   npm install
   npm run start:streamableHttp
   ```

   The server will listen on `http://localhost:3001/mcp`.

3. **HTTPie** (version 3.2.4 or later) installed.

## Example Workflow

1. **Initialize a new MCP session**:

   ```bash
   cd scripts
   ./init.sh
   ```

    * Follow on-screen prompts. After the SSE listener opens, press `Ctrl+C` to close it when ready.

2. **Run tool invocations** (using the printed session ID):

   ```bash
   ./post.sh <session-id>
   ```

    * Press Enter between stages to:

        * List tools
        * Call `echo`
        * Run `longRunningOperation` (with progress events)

3. **Open additional SSE listeners** (optional, in new terminals):

   ```bash
   ./get.sh <session-id>
   ```

    * Each instance will show server notifications for that session.

4. **Stopping listeners**

    * Press `Ctrl+C` in any terminal running `init.sh` or `get.sh` to close the SSE connection.
