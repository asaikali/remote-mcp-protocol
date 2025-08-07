# remote-mcp-protocol

This repository contains tools and examples for exploring the **Model Context Protocol (MCP)** Streamable HTTP transport using:

- **`scripts/`** – Bash scripts to interact with MCP servers using raw HTTP requests via `httpie`, capturing detailed request and response traffic.  
  ➔ Ideal for direct CLI-based inspection if you don't have IntelliJ or JetBrains tooling. It also formats JSON responses nicely.

- **`intellij/`** – IntelliJ `.http` files to test MCP server interactions within the IDE's built-in HTTP client.  
  ➔ Useful for interactive exploration with automatic variable handling and structured response viewing.

## Getting Started

### Prerequisites

This project uses [vendir](https://carvel.dev/vendir/) to manage external dependencies. Install vendir:

```bash
# macOS
brew tap carvel-dev/carvel
brew install vendir

# Or download directly from releases
curl -L https://carvel.dev/install.sh | bash
```

### Setup

1. Clone this repository.

2. Pull external dependencies using vendir:

   ```bash
   vendir sync
   ```

   This will download:
   - **MCP Servers** → `resources/servers/` - Reference server implementations  
   - **MCP Inspector** → `resources/inspector/` - Web-based MCP client for visual testing
   - **MCP Specification** → `resources/spec/` - Official MCP protocol specification (tag: 2025-06-18)

3. Run the Everything Server using the downloaded resources:

   ```bash
   cd resources/servers/src/everything
   npm install
   npm run start:streamableHttp
   ```

   Or use the global npm package:
   ```bash
   # SSE transport
   npx @modelcontextprotocol/server-everything sse

   # HTTP streaming transport  
   npx @modelcontextprotocol/server-everything streamableHttp
   ```

   This will start the **Everything Server** locally on port `3001`, exposing all MCP features for testing.

4. See [`scripts/README.md`](scripts/README.md) for script setup and usage instructions.
5. See [`intellij/README.md`](intellij/README.md) for IntelliJ HTTP file usage examples.

## Managing Dependencies

To update dependencies to their latest versions:

```bash
# Update all dependencies
vendir sync

# Check what will be updated (dry-run)
vendir sync --dry-run
```

The `resources/` directory is excluded from git via `.gitignore`, so dependencies are downloaded fresh when you run `vendir sync`.

✅ **Tip:**  
If you prefer a graphical interface, you can use the **[MCP Inspector](https://github.com/modelcontextprotocol/inspector)**, which provides a UI-based client to send requests to MCP servers and see structured summaries of requests, responses, and notifications.  
However, **these scripts and IntelliJ files provide raw HTTP-level visibility**, allowing you to see headers, exact JSON-RPC payloads, and response streaming behavior for debugging and protocol exploration.
