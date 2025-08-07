# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This repository contains tools and examples for exploring the **Model Context Protocol (MCP)** Streamable HTTP transport. It includes:

- **Java Spring Boot application** (`src/main/java/`) - Contains a Spring Boot app with `McpEverythingClient.java` that demonstrates MCP client interactions over HTTP
- **Bash scripts** (`scripts/`) - HTTPie-based scripts for raw HTTP-level MCP protocol exploration
- **IntelliJ HTTP files** (`intellij/`) - `.http` files for testing MCP interactions within IntelliJ IDE
- **Test resources** (`test/`) - Contains MCP Inspector and various MCP servers (pulled via vendir)
- **Docker support** (`docker/`) - Containerization setup for the project

## Common Development Commands

### Java/Maven Commands
- **Build**: `./mvnw clean compile` - Compiles the Spring Boot application
- **Test**: `./mvnw test` - Runs unit tests
- **Run application**: `./mvnw spring-boot:run` - Starts the Spring Boot application
- **Package**: `./mvnw clean package` - Creates executable JAR

### MCP Server Setup (for testing)
Start the Everything Server locally:
```bash
cd test/servers/src/everything
npm install
npm run start:streamableHttp  # Runs on http://localhost:3001/mcp
```

Alternatively, use npx:
```bash
npx @modelcontextprotocol/server-everything streamableHttp
```

### Script Usage
- **Initialize MCP session**: `cd scripts && ./init.sh`
- **Run tool invocations**: `./post.sh <session-id>`
- **Open SSE listener**: `./get.sh <session-id>`

## Architecture

### Core Components
- **`McpEverythingClient.java`** - Demonstrates complete MCP client flow:
  1. Initialize connection with server
  2. Send initialized notification  
  3. List available tools
  4. Invoke tools (echo, long-running operations)
  5. Handle progress tokens and streaming responses

- **Transport Layers**:
  - Uses Spring WebFlux `WebClient` for HTTP requests
  - Handles `mcp-session-id` header for session management
  - Supports both JSON responses and Server-Sent Events (SSE)

### MCP Protocol Flow
The client implements the standard MCP handshake:
1. **Initialize** - Establish protocol version and capabilities
2. **Initialized notification** - Confirm connection ready
3. **Tool operations** - List and invoke available tools
4. **Progress tracking** - Handle long-running operations with progress tokens

### Key Features
- Interactive CLI with step-by-step prompts
- Support for both simple and streaming tool responses
- Session management with proper header handling
- Progress notification handling for long-running operations

## Testing Dependencies

The `test/` directory contains vendored dependencies:
- **MCP Inspector** - UI-based MCP client for visual testing
- **MCP Servers** - Collection of reference server implementations
- These are managed by `vendir` (see `vendir.yml` and `vendir.lock.yml`)

## Docker Support

- **Dockerfile** and **docker-compose.yaml** available in `docker/` directory
- **entry-point.sh** script for container initialization