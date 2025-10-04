# Quick Reference Guide

Common commands and operations for the Ada Server setup.

## Essential Commands

### Starting & Stopping

```bash
# Start the server
docker-compose up -d

# Start and view logs
docker-compose up

# Stop the server
docker-compose down

# Stop and remove all data (WARNING: deletes models)
docker-compose down -v
```

### Viewing Logs

```bash
# View all logs
docker-compose logs

# Follow logs in real-time
docker-compose logs -f

# View last 50 lines
docker-compose logs --tail=50

# View logs for specific service
docker-compose logs ollama
```

### Container Management

```bash
# Check container status
docker-compose ps

# Restart the container
docker-compose restart

# Rebuild the container
docker-compose up -d --build

# Execute command in container
docker exec minecraft-ollama-server <command>
```

## Model Management

### Installing Models

```bash
# Using the setup script (recommended)
./scripts/setup-models.sh

# Windows PowerShell
.\scripts\setup-models.ps1

# Install specific model
docker exec minecraft-ollama-server ollama pull llama3.2:1b
docker exec minecraft-ollama-server ollama pull phi3:mini
docker exec minecraft-ollama-server ollama pull mistral
```

### Listing Models

```bash
# List all installed models
docker exec minecraft-ollama-server ollama list

# Show model details
docker exec minecraft-ollama-server ollama show llama3.2:1b
```

### Removing Models

```bash
# Remove a specific model
docker exec minecraft-ollama-server ollama rm llama3.2:1b
```

## API Testing

### Basic Health Check

```bash
# Check if API is running
curl http://localhost:11434/api/tags

# Pretty print with jq
curl http://localhost:11434/api/tags | jq
```

### Generate Text

```bash
# Simple generation
curl http://localhost:11434/api/generate -d '{
  "model": "llama3.2:1b",
  "prompt": "Say hello!",
  "stream": false
}'

# With pretty output
curl http://localhost:11434/api/generate -d '{
  "model": "llama3.2:1b",
  "prompt": "Tell me a Minecraft fact.",
  "stream": false
}' | jq -r '.response'
```

### Chat Completion

```bash
# Simple chat
curl http://localhost:11434/api/chat -d '{
  "model": "llama3.2:1b",
  "messages": [
    {"role": "user", "content": "Hello!"}
  ],
  "stream": false
}' | jq -r '.message.content'
```

### Run Demo Tests

```bash
# Run all demo scenarios
./scripts/test-demo.sh
```

## Troubleshooting

### Check Container Status

```bash
# Is the container running?
docker ps | grep minecraft-ollama-server

# Check container health
docker inspect minecraft-ollama-server | jq '.[0].State.Health'

# View container resource usage
docker stats minecraft-ollama-server
```

### Network Issues

```bash
# Test connectivity to container
curl -v http://localhost:11434/

# Check port binding
docker port minecraft-ollama-server

# Test from inside container
docker exec minecraft-ollama-server curl localhost:11434/api/tags
```

### Disk Space

```bash
# Check Docker disk usage
docker system df

# Check volume size
docker volume ls
docker volume inspect minecraft-ollama_ollama_data

# Clean up unused resources
docker system prune
```

### Reset Everything

```bash
# Complete reset (WARNING: deletes all models)
docker-compose down -v
docker system prune -a
docker-compose up -d
```

## Environment Variables

### Common Configuration

```bash
# Create config file from template
cp config/ollama.env.example config/ollama.env

# Edit configuration
nano config/ollama.env  # or vim, code, etc.
```

### Key Variables

```env
OLLAMA_HOST=0.0.0.0           # Bind address
OLLAMA_MAX_CONTEXT=4096       # Max context length
OLLAMA_NUM_PARALLEL=4         # Parallel requests
OLLAMA_DEBUG=false            # Enable debug logging
```

## Development Workflow

### Typical Session

```bash
# 1. Start server
cd server
docker-compose up -d

# 2. Check it's running
docker-compose logs -f

# 3. Install models (if needed)
./scripts/setup-models.sh

# 4. Test the API
curl http://localhost:11434/api/tags

# 5. Run your Minecraft mod
# ... develop and test ...

# 6. Stop server when done
docker-compose down
```

### Making Changes

```bash
# 1. Edit Dockerfile or docker-compose.yml
nano Dockerfile

# 2. Rebuild container
docker-compose down
docker-compose up -d --build

# 3. Verify changes
docker-compose logs -f
```

## Performance Tips

### Speed Up Responses

```bash
# Use smaller models
docker exec minecraft-ollama-server ollama pull llama3.2:1b

# Reduce context size in prompt
# Use shorter, more focused prompts

# Pre-warm the model
curl http://localhost:11434/api/generate -d '{
  "model": "llama3.2:1b",
  "prompt": "Warmup",
  "stream": false
}'
```

### Resource Allocation

```bash
# Increase Docker resources
# Docker Desktop → Settings → Resources
# - CPU: 4+ cores
# - Memory: 8+ GB
# - Disk: 20+ GB
```

## Backup & Restore

### Backup Models

```bash
# Create backup directory
mkdir -p backup

# Backup volume
docker run --rm \
  -v minecraft-ollama_ollama_data:/data \
  -v $(pwd)/backup:/backup \
  alpine tar czf /backup/models-backup.tar.gz -C /data .
```

### Restore Models

```bash
# Restore from backup
docker run --rm \
  -v minecraft-ollama_ollama_data:/data \
  -v $(pwd)/backup:/backup \
  alpine tar xzf /backup/models-backup.tar.gz -C /data
```

## Useful Links

- **Full Setup Guide**: [SERVER_SETUP.md](../SERVER_SETUP.md)
- **Architecture**: [ARCHITECTURE.md](./ARCHITECTURE.md)
- **Ollama API Docs**: https://github.com/ollama/ollama/blob/main/docs/api.md
- **Available Models**: https://ollama.com/library

## Common API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/tags` | GET | List available models |
| `/api/generate` | POST | Generate text completion |
| `/api/chat` | POST | Chat completion |
| `/api/pull` | POST | Download a model |
| `/api/push` | POST | Upload a model |
| `/api/create` | POST | Create custom model |
| `/api/delete` | DELETE | Remove a model |

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | General error |
| 125 | Docker daemon error |
| 126 | Command cannot execute |
| 127 | Command not found |
| 137 | Container killed (OOM) |

## Getting Help

```bash
# Docker help
docker --help
docker-compose --help

# Ollama help
docker exec minecraft-ollama-server ollama --help

# View this guide
cat QUICK_REFERENCE.md
```
