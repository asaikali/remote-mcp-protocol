# MCP Services Docker Configuration

This directory contains Docker configuration for Model Context Protocol (MCP) services used in our Docker Compose setup.

## Services Provided

**MCP Inspector** - Web-based GUI for connecting to and debugging MCP servers
- **Port**: 6274 (HTTP), 6277 (WebSocket)  
- **URL**: http://localhost:6274
- **Purpose**: Visual interface for testing MCP server interactions

**Everything SSE Server** - MCP server using Server-Sent Events transport
- **Port**: 3001
- **Endpoint**: http://localhost:3001/sse
- **Purpose**: Streaming MCP server for real-time communication

**Everything Streamable Server** - MCP server using HTTP streaming transport  
- **Port**: 4001 (external), 3001 (internal)
- **Endpoint**: http://localhost:4001/mcp
- **Purpose**: HTTP-based MCP server with streaming support

## Docker Configuration

### Dockerfile
Multi-purpose Node.js image that can run different MCP services:

**Base Image**: `node:22-alpine` - Lightweight Node.js runtime

**Installed Packages**:
- `@modelcontextprotocol/server-everything` - Reference MCP server implementation  
- `@modelcontextprotocol/inspector` - Web-based MCP debugging tool

**Entry Point**: `/app/entry-point.sh` - Routes commands to appropriate services based on arguments

### entry-point.sh
Service router script that starts different MCP services based on the command argument:

```bash
case "$1" in
  sse)
    # Start Server-Sent Events MCP server
    exec npx @modelcontextprotocol/server-everything sse
    ;;
  streamableHttp)
    # Start HTTP streaming MCP server  
    exec npx @modelcontextprotocol/server-everything streamableHttp
    ;;
  inspector)
    # Configure and start MCP Inspector GUI
    export DANGEROUSLY_OMIT_AUTH=true
    export MCP_INSPECTOR_HOST=0.0.0.0
    export MCP_AUTO_OPEN_ENABLED=false
    exec npx @modelcontextprotocol/inspector
    ;;
esac
```

## Docker Compose Integration

Each service uses the same base image (`mcp-everything:latest`) but with different entry point arguments:

```yaml
everything-sse:
  command: ["/app/entry-point.sh", "sse"]
  ports: ["3001:3001"]

everything-streamable:  
  command: ["/app/entry-point.sh", "streamableHttp"]
  ports: ["4001:3001"]  # External port 4001 maps to internal 3001
  
mcp-inspector:
  command: ["/app/entry-point.sh", "inspector"]
  ports: ["6274:6274", "6277:6277"]
```

## Network Communication

**Container-to-Container**: Services communicate using Docker's internal DNS
- `everything-sse:3001` - SSE server
- `everything-streamable:3001` - Streamable server

**Container-to-Host**: Use `host.docker.internal` to connect from containers to services running on your host machine
- Example: `http://host.docker.internal:3001`

## Using the MCP Inspector

The MCP Inspector is a web-based tool for testing and debugging MCP server connections. Understanding network connectivity is crucial for successful connections.

### Quick Start

1. **Start services**: `compose up` (from parent directory)
2. **Open inspector**: http://localhost:6274  
3. **View connection information**: `compose info` shows all URLs with **bold networking guidance**
4. **Connect to MCP servers** using the appropriate URLs below

### Understanding Network Connectivity

**Key Concept**: The MCP Inspector runs inside a Docker container, so it needs to use Docker network addresses to reach other services, not `localhost`.

#### Connecting to Containerized MCP Servers

When connecting to MCP servers that are also running in Docker containers, use Docker's internal service names:

**SSE Server Connection**:
- ❌ **Wrong**: `http://localhost:3001/sse` (Inspector can't reach localhost)
- ✅ **Correct**: `http://everything-sse:3001/sse` (Docker service name)

**Streamable Server Connection**:  
- ❌ **Wrong**: `http://localhost:4001/mcp` (Inspector can't reach localhost)
- ✅ **Correct**: `http://everything-streamable:3001/mcp` (Docker service name + internal port)

**Why this works**: Docker Compose creates internal DNS entries for each service, allowing containers to communicate using service names.

#### Connecting to Host-Running MCP Servers

If you're running MCP servers on your host machine (outside Docker), use the special Docker hostname:

**Host Server Connection**:
- ✅ **Correct**: `http://host.docker.internal:3001/mcp` (Connect to port on your Mac)
- ✅ **Correct**: `http://host.docker.internal:8080/api` (Any port on your host)

**Why this works**: `host.docker.internal` is a special hostname that Docker provides to access services running on the host machine from inside containers.

### Connection Examples

**Testing Containerized Servers**:
1. Open Inspector: http://localhost:6274
2. In the connection form, enter: `http://everything-sse:3001/sse`
3. Click "Connect" to test the SSE server

**Testing Host-Running Servers**:
1. Start your MCP server on your Mac (e.g., port 8080)
2. In the Inspector, enter: `http://host.docker.internal:8080/mcp`  
3. Click "Connect" to test your server

### Troubleshooting Connections

**"Connection refused" errors**: 
- Verify the MCP server is actually running (`compose ps` for containerized servers)
- Check you're using the correct URL format (service names for containers, `host.docker.internal` for host)
- Ensure ports are correctly mapped in compose.yaml

**"Service not found" errors**:
- Use `docker network ls` and `docker network inspect` to verify service names
- Confirm all services are on the same Docker network

## Customization

**Port Configuration**: All ports are configurable via environment variables in the parent compose system:
- `MCP_SSE_PORT` - SSE server port (default: 3001)
- `MCP_STREAMABLE_PORT` - Streamable server port (default: 4001)  
- `MCP_INSPECTOR_PORT` - Inspector GUI port (default: 6274)
- `MCP_INSPECTOR_WS_PORT` - Inspector WebSocket port (default: 6277)

**Adding MCP Servers**: To add custom MCP servers, extend the Dockerfile or create additional service definitions in the parent compose.yaml.