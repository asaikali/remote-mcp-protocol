#!/usr/bin/env bash
set -e

PROTOCOL_VERSION="2025-06-18"

# Check if message URL is provided as argument
if [ -z "$1" ]; then
  # Try to read from temp file left by get.sh
  if [ -f /tmp/mcp_message_url ]; then
    MESSAGE_URL=$(cat /tmp/mcp_message_url)
    rm -f /tmp/mcp_message_url
  else
    echo "❌ Usage: $0 <MESSAGE_URL>" >&2
    echo "   Example: $0 \"http://localhost:3001/message?sessionId=abc123\"" >&2
    exit 1
  fi
else
  MESSAGE_URL="$1"
fi

echo "Using message URL: $MESSAGE_URL"

########## [1] INITIALIZE ##########
echo
echo "============================================================"
echo "  STAGE 1: INITIALIZE"
echo "============================================================"
echo
echo "Sending INITIALIZE request..."

http --verbose --print=HhBb POST "$MESSAGE_URL" \
  "Content-Type: application/json" \
  <<< '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "'"$PROTOCOL_VERSION"'",
      "capabilities": {
        "roots": { "listChanged": true },
        "sampling": {},
        "elicitation": {}
      },
      "clientInfo": {
        "name": "HttpieTestClient",
        "version": "0.1"
      }
    }
  }'

echo "✅ Initialize request sent."

########## [2] INITIALIZED NOTIFICATION ##########
echo
echo "============================================================"
echo "  STAGE 2: INITIALIZED NOTIFICATION"
echo "============================================================"
echo
echo "Sending INITIALIZED notification..."

http --verbose --print=HhBb POST "$MESSAGE_URL" \
  "Content-Type: application/json" \
  <<< '{
    "jsonrpc": "2.0",
    "method": "notifications/initialized"
  }'

echo "✅ Initialized notification sent."

########## [3] LIST TOOLS ##########
echo
echo "============================================================"
echo "  STAGE 3: LIST TOOLS"
echo "============================================================"
echo
echo "Listing available tools..."

http --verbose --print=HhBb POST "$MESSAGE_URL" \
  "Content-Type: application/json" \
  <<< '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list"
  }'

echo "✅ Tools list request sent."

########## [4] LIST RESOURCES ##########
echo
echo "============================================================"
echo "  STAGE 4: LIST RESOURCES"
echo "============================================================"
echo
echo "Listing available resources..."

http --verbose --print=HhBb POST "$MESSAGE_URL" \
  "Content-Type: application/json" \
  <<< '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "resources/list"
  }'

echo "✅ Resources list request sent."

echo
echo "🎉 MCP HTTP+SSE protocol messages completed successfully!"
echo "💡 Check the SSE stream in get.sh for any server responses or events."
