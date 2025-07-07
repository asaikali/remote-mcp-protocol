#!/usr/bin/env bash
set -e

# Usage: ./get.sh <session-id>
SESSION_ID=$1
BASE_URL="http://localhost:3001/mcp"

if [ -z "$SESSION_ID" ]; then
  echo "Usage: $0 <session-id>"
  exit 1
fi

echo "============================================================"
echo "  SSE GET LISTENER"
echo "  Session ID: $SESSION_ID"
echo "============================================================"
echo

# Listen for all server-initiated notifications on this session
http --stream --verbose --print=HhBb GET "$BASE_URL" \
  Accept:text/event-stream \
  Mcp-Session-Id:$SESSION_ID
