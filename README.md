# remote-mcp-protocol

This repository contains tools and examples for exploring the **Model Context Protocol (MCP)** Streamable HTTP transport using:

- **`scripts/`** – Bash scripts to interact with MCP servers using raw HTTP requests via `httpie`, capturing detailed request and response traffic.  
  ➔ Ideal for direct CLI-based inspection if you don't have IntelliJ or JetBrains tooling. It also formats JSON responses nicely.

- **`intellij/`** – IntelliJ `.http` files to test MCP server interactions within the IDE's built-in HTTP client.  
  ➔ Useful for interactive exploration with automatic variable handling and structured response viewing.

## Getting Started

1. Clone this repository.
2. Clone and run the [Everything Server](https://github.com/modelcontextprotocol/servers) to use as your test MCP server:

   ```bash
   git clone https://github.com/modelcontextprotocol/servers.git
   cd servers/src/everything
   npm install
   npm run start:streamableHttp
   ```
or
```bash
npx @modelcontextprotocol/server-everything sse
```

or 
```bash
npx @modelcontextprotocol/server-everything streamableHttp
```

This will start the **Everything Server** locally on port `3001`, exposing all MCP features for testing.

3. See [`scripts/README.md`](scripts/README.md) for script setup and usage instructions.
4. See [`intellij/README.md`](intellij/README.md) for IntelliJ HTTP file usage examples.

✅ **Tip:**  
If you prefer a graphical interface, you can use the **[MCP Inspector](https://github.com/modelcontextprotocol/inspector)**, which provides a UI-based client to send requests to MCP servers and see structured summaries of requests, responses, and notifications.  
However, **these scripts and IntelliJ files provide raw HTTP-level visibility**, allowing you to see headers, exact JSON-RPC payloads, and response streaming behavior for debugging and protocol exploration.
