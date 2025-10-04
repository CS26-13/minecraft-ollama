# Server Directory

This directory contains all server-related files for the Minecraft Ollama project.

## Quick Reference

```bash
# Start the server
docker-compose up -d

# View logs
docker-compose logs -f

# Install models
./scripts/setup-models.sh

# Stop the server
docker-compose down
```

## Directory Contents

- **Dockerfile** - Docker image configuration for Ollama
- **docker-compose.yml** - Multi-container orchestration config
- **config/** - Configuration files and environment variables
- **data/** - Shared data directory for application files
- **scripts/** - Utility scripts for setup and maintenance

## Full Documentation

See [SERVER_SETUP.md](../SERVER_SETUP.md) in the root directory for complete setup instructions.

## Development Notes

### Adding New Models

To add models to the setup script, edit `scripts/setup-models.sh` and add:

```bash
docker exec minecraft-ollama-server ollama pull <model-name>
```

### Custom Configuration

1. Copy `config/ollama.env.example` to `config/ollama.env`
2. Modify settings as needed
3. Add the env_file directive to docker-compose.yml:
   ```yaml
   services:
     ollama:
       env_file:
         - ./config/ollama.env
   ```
4. Restart the container

### Accessing Logs

```bash
# All logs
docker-compose logs

# Follow logs (real-time)
docker-compose logs -f

# Last 100 lines
docker-compose logs --tail=100
```

### Backup Models

Models are stored in a Docker volume. To backup:

```bash
# Create backup
docker run --rm -v minecraft-ollama_ollama_data:/data -v $(pwd)/backup:/backup alpine tar czf /backup/ollama-models-backup.tar.gz -C /data .

# Restore backup
docker run --rm -v minecraft-ollama_ollama_data:/data -v $(pwd)/backup:/backup alpine tar xzf /backup/ollama-models-backup.tar.gz -C /data
```

## API Endpoints

Once running, the following endpoints are available:

- `http://localhost:11434/` - Ollama service
- `http://localhost:11434/api/tags` - List available models
- `http://localhost:11434/api/generate` - Generate text
- `http://localhost:11434/api/chat` - Chat completion

See [Ollama API docs](https://github.com/ollama/ollama/blob/main/docs/api.md) for more details.
