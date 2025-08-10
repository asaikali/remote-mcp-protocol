FROM node:22-alpine

WORKDIR /app

# Install both the MCP server and inspector tools
RUN npm install -g \
    @modelcontextprotocol/server-everything@latest \
    @modelcontextprotocol/inspector@latest

# Copy entry point script
COPY entry-point.sh /app/entry-point.sh
RUN chmod +x /app/entry-point.sh

# Default bind address for inspector (makes UI accessible from outside)
ENV HOST=0.0.0.0

# Default port exposure (used by sse/streamable/inspector)
EXPOSE 3001 6274 6277

CMD ["/app/entry-point.sh"]
