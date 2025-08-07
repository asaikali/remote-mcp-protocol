#!/bin/bash
set -euo pipefail

# Config
IMAGE_NAME=mcp-everything
IMAGE_TAG=latest

# Build
echo "🛠️ Building Docker image: ${IMAGE_NAME}:${IMAGE_TAG} ..."
docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" -f Dockerfile .

echo "✅ Build complete: ${IMAGE_NAME}:${IMAGE_TAG}"
