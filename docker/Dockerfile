FROM node:22-alpine

WORKDIR /app

# Install the everything server and mcp inspector locally
RUN npm install -g \
      @modelcontextprotocol/server-everything@latest \
      @modelcontextprotocol/inspector@latest

# Copy entrypoint
COPY entry-point.sh /app/entry-point.sh
RUN chmod +x /app/entry-point.sh

# Set default target to `sse`
ENV TARGET=sse

# Only expose the default SSE port
EXPOSE 3001

CMD ["/app/entry-point.sh"]
