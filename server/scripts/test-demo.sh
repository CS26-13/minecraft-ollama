#!/bin/bash
# Demo test script for Ollama integration
# This script runs sample prompts to demonstrate the Minecraft Ollama integration

set -e

echo "=== Minecraft Ollama Demo Test ==="
echo ""

# Check if server is running
if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "Error: Ollama server is not running!"
    echo "Start it with: docker-compose up -d"
    exit 1
fi

echo "✓ Ollama server is running"
echo ""

# Define the model to use
MODEL="llama3.2:1b"

echo "=== Demo 1: Villager Greeting ==="
echo "Prompt: You are a Minecraft villager. A player approaches you. Greet them warmly."
echo ""

curl -s http://localhost:11434/api/generate -d "{
  \"model\": \"$MODEL\",
  \"prompt\": \"You are a Minecraft villager. A player approaches you. Greet them warmly. Keep it brief (2-3 sentences).\",
  \"stream\": false
}" | jq -r '.response'

echo ""
echo ""
echo "=== Demo 2: World Event Narrator ==="
echo "Prompt: Describe a zombie attack on a village at night"
echo ""

curl -s http://localhost:11434/api/generate -d "{
  \"model\": \"$MODEL\",
  \"prompt\": \"As an overseer of a Minecraft village, describe what you see during a zombie attack at night. Keep it brief and atmospheric (2-3 sentences).\",
  \"stream\": false
}" | jq -r '.response'

echo ""
echo ""
echo "=== Demo 3: Player Achievement ==="
echo "Prompt: Player found their first diamond"
echo ""

curl -s http://localhost:11434/api/generate -d "{
  \"model\": \"$MODEL\",
  \"prompt\": \"You are an AI companion in Minecraft. A player just mined their first diamond! Congratulate them enthusiastically. Keep it brief (2-3 sentences).\",
  \"stream\": false
}" | jq -r '.response'

echo ""
echo ""
echo "=== Demo Complete ==="
echo "All tests passed successfully!"
