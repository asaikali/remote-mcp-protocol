# MCP Docker Environment

This Docker environment provides a complete setup for exploring the Model Context Protocol (MCP) with multiple server implementations, a web-based inspector GUI, and a PostgreSQL database for development.

## Quick Start

### Method 1: Using the Compose Script (Recommended)

The `compose` script (located in the docker/ directory) provides a simple interface to manage all services (MCP, PostgreSQL, and Observability):

```bash
# From the docker directory - Start ALL services (MCP + PostgreSQL + Observability):
cd docker && ./compose up

# Show connection information for all services:
./compose info

# View logs from all services:
./compose logs

# Follow logs from all services:
./compose logs -f

# Stop all services:
./compose down

# Clean everything (stops services, removes volumes):
./compose clean

```

**Note**: If you have [direnv](https://direnv.net) installed and run `direnv allow`, the compose script will be available in your PATH from anywhere within the repository.

## Docker Directory Structure

The Docker environment is organized into profile-specific subdirectories:

```
docker/
├── mcp/
│   ├── Dockerfile          # MCP services image
│   └── entry-point.sh      # MCP container entry point
├── postgres/
│   ├── init.sql           # PostgreSQL database initialization
│   └── pgadmin_servers.json  # pgAdmin server configuration
├── observability/
│   ├── config/
│   │   ├── otel-collector.yaml    # OpenTelemetry Collector configuration
│   │   ├── prometheus.yaml        # Prometheus scrape configuration
│   │   └── tempo.yaml             # Tempo tracing backend configuration
│   └── grafana/
│       ├── grafana.ini            # Grafana server configuration
│       └── provisioning/          # Auto-provisioned resources
│           ├── datasources/       # Prometheus, Tempo, Loki connections
│           ├── dashboards/        # Pre-built Spring Boot dashboards
│           └── alerting/          # Alert rules
└── README.md              # This documentation
```

This modular structure allows for:
- **Clean separation** of concerns between MCP, PostgreSQL, and Observability
- **Independent builds** for each service type
- **Easy maintenance** and updates per service group
- **Flexible deployment** with profile-based service selection

## Port Configuration

The environment supports configurable ports for all services via environment variables. Default ports:

**MCP Services:**
- **SSE Server**: 3001
- **Streamable HTTP Server**: 4001  
- **Inspector GUI**: 6274
- **Inspector WebSocket**: 6277

**PostgreSQL Services:**
- **PostgreSQL Database**: 15432
- **pgAdmin Web Interface**: 15433

**Observability Services:**
- **Grafana Dashboard**: 3000
- **Prometheus Metrics**: 9090
- **Loki Logs**: 3100
- **Tempo Traces**: 3200 (OTLP), 9411 (Zipkin)
- **OpenTelemetry Collector**: 4317 (gRPC), 4318 (HTTP)

### Configuring Ports

**Option 1: .env.local file (Recommended for local development)**
```bash
# Copy the example template
cp .env.local.example .env.local

# Edit your local configuration (gitignored - won't be committed)
vim .env.local

# Uncomment and modify any ports you want to override:
MCP_SSE_PORT=5001
MCP_STREAMABLE_PORT=5002
GRAFANA_PORT=8000
PROMETHEUS_PORT=8090
```

**Option 2: Environment variables**
```bash
# Set environment variables
export MCP_SSE_PORT=5001
export MCP_STREAMABLE_PORT=5002
export GRAFANA_PORT=8000

# Then run compose
compose up
```

**Option 3: Inline with commands**
```bash
# Override ports for a single run
MCP_SSE_PORT=5001 GRAFANA_PORT=8000 compose up
```

### Configuration Priority

The system loads configuration in this order (highest to lowest priority):

1. **Environment variables** - `export MCP_SSE_PORT=5001`
2. **`.env.local`** - Personal overrides (gitignored)
3. **`.env`** - Team defaults (committed to git)
4. **Script defaults** - Hardcoded fallbacks

This means you can safely customize ports in `.env.local` without affecting other developers or accidentally committing your changes.

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

### MCP Services (Profile: `mcp`)

| Service | Default Port | URL | Description |
|---------|--------------|-----|-------------|
| Inspector GUI | 6274* | http://localhost:6274 | Web interface for inspecting MCP servers |
| Inspector WebSocket | 6277* | - | WebSocket proxy for the inspector |
| SSE Server | 3001* | http://localhost:3001/sse | Server-Sent Events MCP server |
| Streamable HTTP Server | 4001* | http://localhost:4001/mcp | HTTP streaming MCP server |

### PostgreSQL Services (Profile: `postgres`)

| Service | Default Port | URL | Description |
|---------|--------------|-----|-------------|
| PostgreSQL | 15432* | - | PostgreSQL 17 database server |
| pgAdmin | 15433* | http://localhost:15433 | Web-based PostgreSQL administration |

### Observability Services (Profile: `observability`)

| Service | Default Port | URL | Description |
|---------|--------------|-----|-------------|
| Grafana | 3000* | http://localhost:3000 | Unified observability dashboard |
| Prometheus | 9090* | http://localhost:9090 | Metrics collection and storage |
| Loki | 3100* | http://localhost:3100 | Log aggregation (API only) |
| Tempo | 3200* | http://localhost:3200 | Distributed tracing (OTLP) |
| Tempo Zipkin | 9411* | http://localhost:9411 | Distributed tracing (Zipkin) |
| OTel Collector | 4317*/4318* | - | OpenTelemetry data collection |

*All ports are configurable via environment variables - see [Port Configuration](#port-configuration) section.

## Compose Script Commands

The `compose` script provides convenient management commands with color-coded output and error handling:

### Available Commands

| Command | Description | Example |
|---------|-------------|---------|
| `build` | Build Docker images | `compose build` or `compose build mcp` |
| `up` | Start containers with status display | `compose up` or `compose up postgres` |
| `status` | Show container status and connection URLs | `compose status` or `compose status mcp` |
| `down` | Stop containers and clean up orphans | `compose down` or `compose down postgres` |
| `clean` | Stop containers and remove volumes | `compose clean` or `compose clean mcp` |
| `fix` | Detect and resolve port conflicts | `compose fix` or `compose fix postgres` |
| `ports` | Show current port configuration | `compose ports` or `compose ports mcp` |
| `logs` | Show container logs | `compose logs` or `compose logs postgres` |

### Profile Support

All commands support optional profiles to operate on specific services:

- **`mcp`**: MCP services only (Inspector, SSE Server, Streamable Server)
- **`postgres`**: PostgreSQL services only (PostgreSQL, pgAdmin)
- **`observability`**: Observability stack only (Grafana, Prometheus, Loki, Tempo, OTel Collector)
- **`all`** or no profile: All services (default behavior)

### Usage Examples

```bash
# Build and start everything
compose build
compose up

# Check what's running and get connection URLs
compose status

# Check current port configuration
compose ports

# Start only observability stack for monitoring other applications
compose up observability

# Start MCP services with observability for development
compose up mcp observability

# View logs from all containers
compose logs

# View logs from specific service with options
compose logs grafana --tail 50

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

## Observability Stack Integration

The integrated observability stack provides comprehensive monitoring capabilities for Spring Boot applications and other services:

### Key Components

- **Grafana**: Unified dashboard for visualizing metrics, logs, and traces
- **Prometheus**: Time-series metrics collection and storage
- **Loki**: Log aggregation and querying
- **Tempo**: Distributed tracing storage
- **OpenTelemetry Collector**: Telemetry data collection and processing

### Integration Patterns

**Direct Integration** (Spring Boot → Backends):
```yaml
# Spring Boot sends data directly to observability backends
Spring App → Prometheus (metrics scraping)
Spring App → Tempo (Zipkin traces)
Spring App → Loki (log shipping)
```

**OpenTelemetry Integration** (Spring Boot → OTel Collector → Backends):
```yaml  
# Spring Boot sends OTLP data to collector, which routes to backends
Spring App → OTel Collector → Prometheus/Tempo/Loki
```

### Quick Start with Spring Boot

1. Start the observability stack: `compose up observability`
2. Configure your Spring Boot application with observability dependencies
3. Access Grafana at http://localhost:3000 (pre-configured dashboards included)
4. Monitor your application metrics, logs, and traces in one unified interface

The observability stack comes pre-configured with:
- **Datasource connections** between Grafana and all backends
- **Spring Boot dashboards** for JVM metrics, HTTP requests, and database connections  
- **Trace-to-metrics correlation** for linking traces to performance data
- **Log-to-trace correlation** for debugging across telemetry types

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
