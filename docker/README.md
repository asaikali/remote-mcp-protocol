# MCP Docker Environment

This Docker environment provides a complete setup for exploring the Model Context Protocol (MCP) with multiple server implementations and a web-based inspector GUI.

## Quick Start

### 1. Build and Start Services

```bash
# Build the Docker image
docker build -t mcp-everything:latest .

# Start all services
docker compose up -d

# View logs (optional)
docker compose logs -f
```

### 2. Access the Services

Once started, you'll have access to:

- **MCP Inspector GUI**: http://localhost:6274
- **SSE Server**: http://localhost:3001/sse
- **Streamable HTTP Server**: http://localhost:4001/mcp

### 3. Using the Inspector

1. Open http://localhost:6274 in your browser
2. You'll see the MCP Inspector interface
3. To connect to servers, use these URLs in the inspector:

**For containerized MCP servers:**
- SSE Server: `http://everything-sse:3001`  
- Streamable Server: `http://everything-streamable:3001`

**For MCP servers running on your Mac/host:**
- Use: `http://host.docker.internal:PORT` (replace PORT with your server's port)
- Example: `http://host.docker.internal:3001`

### 4. Why Use `host.docker.internal`?

When the inspector (running inside Docker) tries to connect to `http://localhost:3001`, it looks for port 3001 inside the container, not on your Mac. Docker provides `host.docker.internal` as a special hostname that resolves to your host machine's IP address, allowing containerized applications to reach services running on your Mac.

## Available Services

| Service | Port | URL | Description |
|---------|------|-----|-------------|
| Inspector GUI | 6274 | http://localhost:6274 | Web interface for inspecting MCP servers |
| Inspector WebSocket | 6277 | - | WebSocket proxy for the inspector |
| SSE Server | 3001 | http://localhost:3001/sse | Server-Sent Events MCP server |
| Streamable HTTP Server | 4001 | http://localhost:4001/mcp | HTTP streaming MCP server |

## Management Commands

```bash
# Start services
docker compose up -d

# Stop services  
docker compose down

# View logs
docker compose logs

# Restart a specific service
docker compose restart mcp-inspector

# Clean up everything including orphaned containers
docker compose down --remove-orphans
```

---

## How It Works

### Architecture Overview

This setup creates three Docker containers that communicate over a shared Docker network:

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────────┐
│   Your Mac      │    │  Docker Network  │    │   Container Ports   │
│                 │    │                  │    │                     │
│ Browser         │───▶│  Inspector GUI   │───▶│  :6274             │
│ :6274          │    │  :6274           │    │                     │
│                 │    │                  │    │  SSE Server         │
│ MCP Server      │    │  Inspector Proxy │    │  :3001              │
│ :3001          │◀───│  :6277           │    │                     │
│                 │    │                  │    │  Streamable Server  │
└─────────────────┘    │                  │    │  :3001 → :4001     │
                        └──────────────────┘    └─────────────────────┘
```

### Container Details

#### 1. Base Image (`mcp-everything:latest`)
- **Base**: Node.js 22 Alpine Linux
- **Packages**: 
  - `@modelcontextprotocol/server-everything` - Reference MCP server implementation
  - `@modelcontextprotocol/inspector` - Web-based MCP client/debugger
- **Entry Point**: `/app/entry-point.sh` - Routes commands to appropriate services

#### 2. MCP Inspector Container (`mcp-inspector`)
- **Purpose**: Web-based GUI for connecting to and inspecting MCP servers
- **Ports**: 6274 (HTTP), 6277 (WebSocket proxy)  
- **Configuration**:
  - Authentication disabled (`DANGEROUSLY_OMIT_AUTH=true`)
  - Binds to `0.0.0.0` to accept external connections
  - Auto-browser opening disabled for container usage

#### 3. SSE Server Container (`everything-sse`)
- **Purpose**: MCP server using Server-Sent Events transport
- **Port**: 3001
- **Endpoint**: `/sse`
- **Features**: Real-time streaming, connection management

#### 4. Streamable HTTP Server Container (`everything-streamable`)  
- **Purpose**: MCP server using HTTP streaming transport
- **Internal Port**: 3001, **External Port**: 4001
- **Endpoint**: `/mcp`
- **Features**: HTTP-based request/response with streaming support

### Network Communication

#### Container-to-Container
Containers communicate using Docker's internal DNS:
- `everything-sse:3001` - SSE server
- `everything-streamable:3001` - Streamable server  
- `mcp-inspector:6274` - Inspector GUI

#### Container-to-Host
The special hostname `host.docker.internal` allows containers to reach your Mac:
- Resolves to host machine's IP address
- Enables inspector to connect to MCP servers running on your Mac
- Example: `http://host.docker.internal:3001`

### Entry Point Script Logic

The `entry-point.sh` script routes container startup based on the command argument:

```bash
case "$1" in
  sse|streamableHttp)
    # Start MCP server with specified transport
    exec npx @modelcontextprotocol/server-everything "$1"
    ;;
  inspector)  
    # Configure and start MCP inspector
    export DANGEROUSLY_OMIT_AUTH=true
    export MCP_INSPECTOR_HOST=0.0.0.0
    export MCP_AUTO_OPEN_ENABLED=false
    exec npx @modelcontextprotocol/inspector
    ;;
esac
```

### Docker Compose Configuration

Each service uses the same base image but with different entry point arguments:

```yaml
services:
  everything-sse:
    command: ["/app/entry-point.sh", "sse"]
    
  everything-streamable:  
    command: ["/app/entry-point.sh", "streamableHttp"]
    
  mcp-inspector:
    command: ["/app/entry-point.sh", "inspector"]
```

This approach allows a single multi-purpose image to serve different roles based on the command argument, simplifying builds and maintenance.