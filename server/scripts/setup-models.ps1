# PowerShell script to pull and setup Ollama models for demo
# For Windows users

Write-Host "=== Minecraft Ollama - Model Setup ===" -ForegroundColor Green
Write-Host "This script will pull recommended models for the demo"
Write-Host ""

# Check if Docker is running
try {
    docker ps | Out-Null
} catch {
    Write-Host "Error: Docker is not running!" -ForegroundColor Red
    Write-Host "Please start Docker Desktop first."
    exit 1
}

# Check if Ollama container is running
$containerRunning = docker ps --filter "name=minecraft-ollama-server" --format "{{.Names}}" 2>$null

if (-not $containerRunning) {
    Write-Host "Error: Ollama container is not running!" -ForegroundColor Red
    Write-Host "Please start it first: docker-compose up -d"
    exit 1
}

Write-Host "Ollama server is running. Pulling models..." -ForegroundColor Green
Write-Host ""

# Pull lightweight models suitable for demo
Write-Host "1. Pulling llama3.2:1b (lightweight, fast responses)..." -ForegroundColor Cyan
docker exec minecraft-ollama-server ollama pull llama3.2:1b

Write-Host ""
Write-Host "2. Pulling phi3:mini (optimized for chat)..." -ForegroundColor Cyan
docker exec minecraft-ollama-server ollama pull phi3:mini

Write-Host ""
Write-Host "=== Setup Complete ===" -ForegroundColor Green
Write-Host ""
Write-Host "Available models:"
docker exec minecraft-ollama-server ollama list
Write-Host ""
Write-Host "You can now use these models for your Minecraft Ollama demo!" -ForegroundColor Green
