# MCP Docker Environment

This Docker environment provides a complete setup for exploring the Model Context Protocol (MCP) with multiple server implementations and a web-based inspector GUI.

## Quick Start

### Method 1: Using the Compose Script (Recommended)

The `compose` script (located at the root of the repository) provides a simple interface to manage the MCP stack:

```bash
# From the repository root:
./compose build

# Or if using direnv (which adds the root to PATH):
compose build

# Start all services
compose start

# Check status and connection info
compose status

# View logs
compose logs

# Stop services
compose stop
```

**Note**: If you have [direnv](https://direnv.net) installed and run `direnv allow`, the compose script will be available in your PATH from anywhere within the repository.

## Port Configuration

The MCP stack uses configurable ports via environment variables. Default ports:

- **SSE Server**: 3001
- **Streamable HTTP Server**: 4001  
- **Inspector GUI**: 6274
- **Inspector WebSocket**: 6277

### Configuring Ports

**Option 1: Edit .env file (Recommended)**
```bash
# Edit the .env file in the project root
vim .env

# Modify port values:
MCP_SSE_PORT=5001
MCP_STREAMABLE_PORT=5002
MCP_INSPECTOR_PORT=5274
MCP_INSPECTOR_WS_PORT=5277
```

**Option 2: Environment variables**
```bash
# Set environment variables
export MCP_SSE_PORT=5001
export MCP_STREAMABLE_PORT=5002

# Then run compose
compose up
```

**Option 3: Inline with commands**
```bash
# Override ports for a single run
MCP_SSE_PORT=5001 MCP_STREAMABLE_PORT=5002 compose up
```

### Check Current Port Configuration
```bash
# Show current port configuration
compose ports
```

### Method 2: Direct Docker Commands

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

| Service | Default Port | URL | Description |
|---------|--------------|-----|-------------|
| Inspector GUI | 6274* | http://localhost:6274 | Web interface for inspecting MCP servers |
| Inspector WebSocket | 6277* | - | WebSocket proxy for the inspector |
| SSE Server | 3001* | http://localhost:3001/sse | Server-Sent Events MCP server |
| Streamable HTTP Server | 4001* | http://localhost:4001/mcp | HTTP streaming MCP server |

*Ports are configurable via environment variables - see [Port Configuration](#port-configuration) section.

## Compose Script Commands

The `compose` script provides convenient management commands with color-coded output and error handling:

### Available Commands

| Command | Description | Example |
|---------|-------------|---------|
| `build` | Build the MCP everything Docker image | `compose build` |
| `up` | Start all containers with status display | `compose up` |
| `status` | Show container status and connection URLs | `compose status` |
| `down` | Stop containers and clean up orphans | `compose down` |
| `clean` | Stop containers and remove volumes | `compose clean` |
| `fix` | Detect and resolve port conflicts | `compose fix` |
| `ports` | Show current port configuration | `compose ports` |
| `logs` | Show container logs (all or specific service) | `compose logs mcp-inspector` |

### Usage Examples

```bash
# Build and start everything
compose build
compose up

# Check what's running and get connection URLs
compose status

# Check current port configuration
compose ports

# View logs from all containers
compose logs

# View logs from specific service with options
compose logs mcp-inspector --tail 50

# If you have port conflicts, fix them automatically
compose fix

# Clean shutdown
compose down
```

### Features

- **Color-coded output**: Green for success, yellow for warnings, red for errors
- **Port conflict detection**: Automatically detects if ports are in use by other containers
- **Connection information**: Shows direct URLs and inspector connection URLs
- **Orphan cleanup**: Removes orphaned containers when stopping services
- **Smart container management**: Uses Docker Compose when possible, falls back to docker commands

## Direct Docker Commands (Alternative)

If you prefer using Docker commands directly:

```bash
# Start services
docker compose up -d

# Stop services  
docker compose down --remove-orphans

# View logs
docker compose logs

# Restart a specific service
docker compose restart mcp-inspector

# Clean up everything including volumes
docker compose down -v --remove-orphans
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
