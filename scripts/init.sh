#!/usr/bin/env bash
set -e

BASE_URL="http://localhost:3001/mcp"
PROTOCOL_VERSION="2025-06-18"
TMP_INIT_RESPONSE=$(mktemp)

########## [1] INITIALIZE ##########
echo
echo "============================================================"
echo "  STAGE 1: INITIALIZE"
echo "============================================================"
echo
http --verbose --print=HhBb POST "$BASE_URL" \
  "Accept: application/json, text/event-stream" \
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
  }' | tee "$TMP_INIT_RESPONSE"

########## [2] EXTRACT SESSION ID ##########
echo
echo "============================================================"
echo "  STAGE 2: EXTRACT SESSION ID"
echo "============================================================"
echo
SESSION_ID=$(grep -i '^mcp-session-id:' "$TMP_INIT_RESPONSE" | awk '{print $2}' | tr -d '\r')
rm -f "$TMP_INIT_RESPONSE"
if [ -z "$SESSION_ID" ]; then
  echo "❌ Failed to extract session ID." >&2
  exit 1
fi
echo "✅ MCP Session ID: $SESSION_ID"
echo

read -p "➡️ Press Enter to send INITIALIZED notification…"

########## [3] INITIALIZED NOTIFICATION ##########
echo
echo "============================================================"
echo "  STAGE 3: INITIALIZED NOTIFICATION"
echo "============================================================"
echo
http --verbose --print=HhBb POST "$BASE_URL" \
  "Accept: application/json, text/event-stream" \
  "Content-Type: application/json" \
  "Mcp-Session-Id: $SESSION_ID" \
  <<< '[
  {
    "jsonrpc": "2.0",
    "method": "notifications/initialized"
  }
]'

echo
echo "✅ Initialized notification sent."
echo "➡️ Next, run: ./post.sh $SESSION_ID to invoke tools or list resources."
echo

read -p "➡️ Press Enter to start SSE listener…"

########## [4] SSE LISTENER ##########
echo
echo "============================================================"
echo "  STAGE 4: SSE LISTENER"
echo "============================================================"
echo
http --verbose --print=HhBb --stream GET "$BASE_URL" \
  "Accept: text/event-stream" \
  "Mcp-Session-Id: $SESSION_ID"
