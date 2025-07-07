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

read -p "➡️ Press Enter to invoke the LONG RUNNING OPERATION tool…"

########## [3] INVOKE LONG RUNNING OPERATION ##########
echo
echo "============================================================"
echo "  STAGE 3: INVOKE LONG RUNNING OPERATION"
echo "============================================================"
echo
http --stream POST http://localhost:3001/mcp \
  Accept:application/json,text/event-stream \
  Content-Type:application/json \
  Mcp-Session-Id:$SESSION_ID \
  <<< '{
    "jsonrpc":"2.0","id":4,"method":"tools/call",
    "params":{
      "name":"longRunningOperation",
      "arguments":{"duration":10,"steps":5},
      "_meta":{"progressToken":"op-1234"}
    }
  }'
