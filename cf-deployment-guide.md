# Cloud Foundry Deployment Guide - MCP Everything Server

This guide explains how to deploy the MCP Everything Server to Cloud Foundry using two different transport modes.

## Prerequisites

1. CF CLI installed and configured
2. Access to a Cloud Foundry foundation
3. Node.js project dependencies resolved

## Deployment Options

### 1. Streamable HTTP Transport

Deploy the server with streamable HTTP transport (recommended for most use cases):

```bash
cf push -f cf-manifest-streamable.yml
```

**Access URL**: `https://mcp-everything-streamable.apps.internal/mcp`

### 2. Server-Sent Events (SSE) Transport  

Deploy the server with SSE transport:

```bash
cf push -f cf-manifest-sse.yml
```

**Access URL**: `https://mcp-everything-sse.apps.internal/mcp`

## Configuration Details

### Common Settings
- **Memory**: 512M (can be adjusted based on load)
- **Buildpack**: Node.js buildpack (auto-detected)
- **Health Check**: HTTP endpoint `/health`
- **Environment**: Production mode

### Environment Variables
- `NODE_ENV=production` - Optimized for production
- `PORT=$PORT` - Uses CF-assigned port

## Post-Deployment

1. **Verify deployment**:
   ```bash
   cf apps
   cf logs mcp-everything-streamable --recent
   cf logs mcp-everything-sse --recent
   ```

2. **Test connectivity**:
   - Streamable: `curl https://mcp-everything-streamable.apps.internal/mcp`
   - SSE: `curl https://mcp-everything-sse.apps.internal/mcp`

## Notes

- Routes use `.apps.internal` - adjust domain to match your CF foundation
- Consider adding external routes if public access is needed
- Monitor memory usage and scale instances as needed
- Both deployments can run simultaneously for different use cases