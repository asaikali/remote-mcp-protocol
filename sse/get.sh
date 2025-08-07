#!/usr/bin/env bash
set -e

BASE_URL="http://localhost:3001/sse"

########## [1] OPEN SSE STREAM TO GET ENDPOINT ##########
echo
echo "============================================================"
echo "  STAGE 1: OPEN SSE STREAM TO GET ENDPOINT"
echo "============================================================"
echo
echo "Opening SSE stream to get message endpoint..."

# Start SSE stream and capture the endpoint event
timeout 10 curl -s -N "$BASE_URL" | while IFS= read -r line; do
  echo "SSE: $line"
  if [[ "$line" == data:* ]]; then
    ENDPOINT_PATH=$(echo "$line" | sed 's/^data: //')
    echo "✅ Got endpoint: $ENDPOINT_PATH"
    echo "$ENDPOINT_PATH" > /tmp/mcp_endpoint
    break
  fi
done

# Extract the endpoint path
if [ ! -f /tmp/mcp_endpoint ]; then
  echo "❌ Failed to get endpoint from SSE stream." >&2
  exit 1
fi

ENDPOINT_PATH=$(cat /tmp/mcp_endpoint)
rm -f /tmp/mcp_endpoint

if [ -z "$ENDPOINT_PATH" ]; then
  echo "❌ Failed to extract endpoint path." >&2
  exit 1
fi

echo "✅ Message endpoint: $ENDPOINT_PATH"
MESSAGE_URL="http://localhost:3001$ENDPOINT_PATH"
echo "✅ Full message URL: $MESSAGE_URL"

# Extract session ID from the endpoint path
SESSION_ID=$(echo "$ENDPOINT_PATH" | grep -o 'sessionId=[^&]*' | cut -d'=' -f2)
if [ -z "$SESSION_ID" ]; then
  echo "❌ Failed to extract session ID from endpoint." >&2
  exit 1
fi
echo "✅ Session ID: $SESSION_ID"

########## [2] START PERSISTENT SSE LISTENER ##########
echo
echo "============================================================"
echo "  STAGE 2: START PERSISTENT SSE LISTENER"
echo "============================================================"
echo
echo "Starting persistent SSE listener..."
echo "Press Ctrl+C to stop the SSE stream"
echo

# Save the message URL for post.sh
echo "$MESSAGE_URL" > /tmp/mcp_message_url

echo "============================================================"
echo "  TO SEND INITIALIZE REQUEST, RUN:"
echo "  ./post.sh \"$MESSAGE_URL\""
echo "============================================================"
echo

# Start persistent SSE listener (this will run until Ctrl+C)
curl -s -N "$BASE_URL" | while IFS= read -r line; do
  timestamp=$(date '+%Y-%m-%d %H:%M:%S')
  echo "[$timestamp] SSE: $line"
done
