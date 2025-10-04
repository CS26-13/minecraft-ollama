#!/bin/bash
# Script to pull and setup Ollama models for demo

set -e

echo "=== Minecraft Ollama - Model Setup ==="
echo "This script will pull recommended models for the demo"
echo ""

# Check if Ollama is running
if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "Error: Ollama server is not running or not accessible at localhost:11434"
    echo "Please start the Docker container first: docker-compose up -d"
    exit 1
fi

echo "Ollama server is running. Pulling models..."
echo ""

# Pull lightweight models suitable for demo
echo "1. Pulling llama3.2:1b (lightweight, fast responses)..."
docker exec minecraft-ollama-server ollama pull llama3.2:1b

echo ""
echo "2. Pulling phi3:mini (optimized for chat)..."
docker exec minecraft-ollama-server ollama pull phi3:mini

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Available models:"
docker exec minecraft-ollama-server ollama list
echo ""
echo "You can now use these models for your Minecraft Ollama demo!"
echo "Test with: curl http://localhost:11434/api/generate -d '{\"model\":\"llama3.2:1b\",\"prompt\":\"Hello!\"}'"
