# IntelliJ .http File Usage

This folder contains an IntelliJ HTTP Client file (`streamable.http`) pre-configured to interact with the MCP “everything” server over the **Streamable HTTP transport**.

## Prerequisites

First, ensure you have the **Everything Server** running locally:

1. Clone the [Everything Server repository](https://github.com/modelcontextprotocol/servers):

   ```bash
   git clone https://github.com/modelcontextprotocol/servers.git
   cd servers/src/everything
   npm install
   npm run start:streamableHttp
   ```

   This will start the server on `http://localhost:3001/mcp`.

## How to Use

1. **Open `streamable.http`** in IntelliJ IDEA.
2. **Configure environment variables** at the top of the file if needed:

   ```http
   @baseUrl = http://localhost:3001/mcp
   @protocolVersion = 2025-06-18
   ```

3. **Execute requests one by one** by clicking the ▶️ icon next to each entry:

    - **Initialize**: establishes a session and **automatically captures** the `Mcp-Session-Id` in `client.global.sessionId`.
    - **Initialized notification**: runs once the session ID is set.
    - **SSE stream**: opens a live event stream to receive server-initiated notifications.
    - **Tool calls**: invoke tools like `echo`, `add`, or `longRunningOperation` (include `_meta.progressToken` for progress events).

✅ The **session ID is captured automatically** by the built-in response handler – no manual copying needed between requests.
