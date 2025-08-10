#!/bin/sh
set -e

case "$1" in
  sse|streamableHttp)
    echo "🟢 Starting MCP everything server with mode: $1"
    exec npx @modelcontextprotocol/server-everything "$1"
    ;;
  inspector)
    echo "🛠️  Launching MCP Inspector (auth disabled)"
    export DANGEROUSLY_OMIT_AUTH=true
    export MCP_INSPECTOR_HOST=0.0.0.0
    export MCP_AUTO_OPEN_ENABLED=false
    exec npx @modelcontextprotocol/inspector
    ;;
  *)
    echo "❌ Unknown mode: $1"
    echo "Usage: docker run ... [sse|streamableHttp|inspector]"
    exit 1
    ;;
esac
