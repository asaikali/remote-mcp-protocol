#!/usr/bin/env bash
set -e

BASE_URL="http://localhost:3001/mcp"
SESSION_ID=$1

if [ -z "$SESSION_ID" ]; then
  echo "Usage: ./post.sh <session-id>"
  exit 1
fi

########## [1] LIST TOOLS ##########
echo
echo "============================================================"
echo "  STAGE 1: LIST TOOLS"
echo "============================================================"
echo
http --verbose --print=HhBb POST "$BASE_URL" \
  "Accept: application/json, text/event-stream" \
  "Content-Type: application/json" \
  "Mcp-Session-Id: $SESSION_ID" \
  <<< '{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list"
}'

read -p "➡️ Press Enter to invoke the echo tool…"

########## [2] INVOKE ECHO TOOL ##########
echo
echo "============================================================"
echo "  STAGE 2: INVOKE ECHO TOOL"
echo "============================================================"
echo
http --verbose --print=HhBb POST "$BASE_URL" \
  "Accept: application/json, text/event-stream" \
  "Content-Type: application/json" \
  "Mcp-Session-Id: $SESSION_ID" \
  <<< '{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "echo",
    "arguments": {
      "message": "Hello from post.sh!"
    }
  }
}'

read -p "➡️ Press Enter to invoke LONG RUNNING OPERATION WITHOUT progressToken…"

########## [3] INVOKE LONG RUNNING OPERATION WITHOUT progressToken ##########
echo
echo "============================================================"
echo "  STAGE 3: LONG RUNNING OPERATION WITHOUT progressToken"
echo "============================================================"
echo
http --stream POST "$BASE_URL" \
  Accept:application/json,text/event-stream \
  Content-Type:application/json \
  Mcp-Session-Id:$SESSION_ID \
  <<< '{
    "jsonrpc":"2.0","id":4,"method":"tools/call",
    "params":{
      "name":"longRunningOperation",
      "arguments":{"duration":10,"steps":5}
    }
  }'

echo
echo "⚠️  Notice: No progress notifications should have been received above."
echo "    See https://modelcontextprotocol.io/specification/2025-06-18/basic/utilities/progress for details."
echo

read -p "➡️ Press Enter to invoke LONG RUNNING OPERATION WITH progressToken…"

########## [4] INVOKE LONG RUNNING OPERATION WITH progressToken ##########
echo
echo "============================================================"
echo "  STAGE 4: LONG RUNNING OPERATION WITH progressToken"
echo "============================================================"
echo
http --stream POST "$BASE_URL" \
  Accept:application/json,text/event-stream \
  Content-Type:application/json \
  Mcp-Session-Id:$SESSION_ID \
  <<< '{
    "jsonrpc":"2.0","id":5,"method":"tools/call",
    "params":{
      "name":"longRunningOperation",
      "arguments":{"duration":10,"steps":5},
      "_meta":{"progressToken":"op-1234"}
    }
  }'

echo
echo "✅ Progress notifications should have been received in the SSE stream."
echo
