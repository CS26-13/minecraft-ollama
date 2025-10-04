# Ada Server Setup Guide

This guide provides instructions for setting up the development environment and Ollama server for the Minecraft Ollama project. The setup uses Docker to ensure consistency across different platforms (Mac, Windows, and Linux).

## Table of Contents
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Directory Structure](#directory-structure)
- [Detailed Setup](#detailed-setup)
- [Model Installation](#model-installation)
- [Testing the Setup](#testing-the-setup)
- [Demo Preparation](#demo-preparation)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### All Platforms
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running
- [Docker Compose](https://docs.docker.com/compose/install/) (included with Docker Desktop)
- Git for version control
- At least 8GB of free disk space for models

### Platform-Specific Notes

#### Mac
- Docker Desktop for Mac (Apple Silicon or Intel)
- Terminal or iTerm2 for command-line operations

#### Windows
- Docker Desktop for Windows with WSL2 backend enabled
- PowerShell or Git Bash for running scripts
- For running shell scripts, use Git Bash or WSL2

#### Linux
- Docker Engine and Docker Compose
- Standard terminal

## Quick Start

1. **Clone the repository** (if not already done):
   ```bash
   git clone https://github.com/CS26-13/minecraft-ollama.git
   cd minecraft-ollama
   ```

2. **Navigate to the server directory**:
   ```bash
   cd server
   ```

3. **Start the Ollama server**:
   ```bash
   docker-compose up -d
   ```

4. **Wait for the server to be ready** (check logs):
   ```bash
   docker-compose logs -f ollama
   ```
   Press `Ctrl+C` to exit log view when you see the server is running.

5. **Install demo models**:
   ```bash
   # On Mac/Linux:
   ./scripts/setup-models.sh
   
   # On Windows (Git Bash/WSL2):
   bash scripts/setup-models.sh
   
   # Or manually:
   docker exec minecraft-ollama-server ollama pull llama3.2:1b
   docker exec minecraft-ollama-server ollama pull phi3:mini
   ```

6. **Verify the setup**:
   ```bash
   curl http://localhost:11434/api/tags
   ```

## Directory Structure

```
minecraft-ollama/
├── server/                      # Server-related files
│   ├── Dockerfile              # Docker image for Ollama
│   ├── docker-compose.yml      # Docker Compose configuration
│   ├── config/                 # Configuration files
│   │   └── ollama.env.example # Environment configuration example
│   ├── data/                   # Shared data directory
│   └── scripts/                # Utility scripts
│       └── setup-models.sh    # Model installation script
├── src/                        # Minecraft mod source code
└── SERVER_SETUP.md            # This file
```

## Detailed Setup

### Step 1: Start the Docker Container

The Docker container runs the Ollama service, which provides the LLM API:

```bash
cd server
docker-compose up -d
```

This command:
- Builds the Docker image (if not already built)
- Starts the Ollama server in detached mode
- Exposes the API on port 11434
- Creates a persistent volume for model storage

### Step 2: Verify Container Status

Check that the container is running:

```bash
docker-compose ps
```

You should see the `minecraft-ollama-server` container with status "Up" and healthy.

### Step 3: Configure Environment (Optional)

If you need custom configuration:

1. Copy the example configuration:
   ```bash
   cp config/ollama.env.example config/ollama.env
   ```

2. Edit `config/ollama.env` with your preferred settings

3. Restart the container:
   ```bash
   docker-compose down
   docker-compose up -d
   ```

## Model Installation

### Recommended Models for Demo

We recommend starting with these lightweight models:

1. **llama3.2:1b** - Fast, lightweight model (1.3GB)
   ```bash
   docker exec minecraft-ollama-server ollama pull llama3.2:1b
   ```

2. **phi3:mini** - Optimized for chat interactions (2.3GB)
   ```bash
   docker exec minecraft-ollama-server ollama pull phi3:mini
   ```

### Alternative Models

For more advanced demos, consider:

- **llama3.2:3b** - Better quality, still relatively fast (2GB)
- **gemma:2b** - Google's efficient model (1.4GB)
- **mistral** - High quality general purpose model (4.1GB)

### Manual Model Installation

To install any model manually:

```bash
docker exec minecraft-ollama-server ollama pull <model-name>
```

### List Installed Models

```bash
docker exec minecraft-ollama-server ollama list
```

## Testing the Setup

### Test 1: API Availability

```bash
curl http://localhost:11434/api/tags
```

Expected output: JSON list of available models

### Test 2: Generate Response

```bash
curl http://localhost:11434/api/generate -d '{
  "model": "llama3.2:1b",
  "prompt": "Hello! Tell me a fun fact about Minecraft.",
  "stream": false
}'
```

### Test 3: Chat Interaction

```bash
curl http://localhost:11434/api/chat -d '{
  "model": "llama3.2:1b",
  "messages": [
    {
      "role": "user",
      "content": "What is your purpose in this Minecraft world?"
    }
  ],
  "stream": false
}'
```

## Demo Preparation

### Pre-Demo Checklist

- [ ] Docker container is running (`docker-compose ps`)
- [ ] At least one model is installed (`docker exec minecraft-ollama-server ollama list`)
- [ ] API is responding (`curl http://localhost:11434/api/tags`)
- [ ] Test generation works (see Test 2 above)
- [ ] Network connectivity is stable

### Demo Scripts

Create test prompts that showcase the integration:

1. **Villager Conversation**:
   - "You are a Minecraft villager. A player approaches you. Greet them warmly."

2. **World Event Narrator**:
   - "Describe what you see as an overseer of a Minecraft village being attacked by zombies at night."

3. **Context-Aware Assistant**:
   - "A player has just mined their first diamond. As their AI companion, what would you say?"

### Performance Tips for Demo

- Use `llama3.2:1b` for fastest responses
- Set `"stream": true` for progressive response display
- Keep prompts concise for quicker responses
- Pre-warm the model with a test request before the demo

## Troubleshooting

### Container Won't Start

```bash
# Check logs
docker-compose logs ollama

# Restart the container
docker-compose down
docker-compose up -d
```

### Port Already in Use

If port 11434 is already in use, modify `docker-compose.yml`:

```yaml
ports:
  - "11435:11434"  # Use different external port
```

### Models Not Downloading

```bash
# Check container has internet access
docker exec minecraft-ollama-server ping -c 3 google.com

# Check available disk space
docker system df

# Clean up unused Docker resources if needed
docker system prune -a
```

### Performance Issues

1. **Allocate more resources to Docker**:
   - Docker Desktop → Settings → Resources
   - Increase CPU and Memory limits

2. **Use smaller models**:
   - `llama3.2:1b` instead of larger variants

3. **Reduce concurrent requests**:
   - Set `OLLAMA_NUM_PARALLEL=1` in config

### Platform-Specific Issues

#### Mac (Apple Silicon)
- Ensure Docker Desktop is using the Virtualization framework
- Some models may perform better than others on ARM architecture

#### Windows
- Ensure WSL2 is properly configured
- Use Git Bash or WSL2 for running shell scripts
- Forward ports properly from WSL2 if needed

#### Linux
- Ensure Docker daemon is running: `sudo systemctl start docker`
- Add user to docker group: `sudo usermod -aG docker $USER`
- Log out and back in for group changes to take effect

## Stopping the Server

```bash
# Stop the container (preserves models)
docker-compose down

# Stop and remove volumes (deletes models)
docker-compose down -v
```

## Next Steps

After completing the server setup:

1. Review the main [README.md](./README.md) for project goals
2. Explore the Minecraft mod source code in `src/`
3. Check [CONTRIBUTING.md](./.github/CONTRIBUTING.md) for development guidelines
4. Start integrating Ollama API calls into the Minecraft mod

## Additional Resources

- [Ollama Documentation](https://github.com/ollama/ollama/blob/main/docs/api.md)
- [Ollama Model Library](https://ollama.com/library)
- [Docker Documentation](https://docs.docker.com/)
- [Minecraft Forge Documentation](https://docs.minecraftforge.net/)

## Support

If you encounter issues not covered in this guide:

1. Check the [Issues](https://github.com/CS26-13/minecraft-ollama/issues) page
2. Review Docker and Ollama logs
3. Open a new issue with detailed information about your problem
