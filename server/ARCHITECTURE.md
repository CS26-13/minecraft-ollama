# Server Architecture

This document describes the architecture of the Ada Server setup for the Minecraft Ollama project.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Host System                          │
│                (Mac / Windows / Linux)                  │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │          Docker Environment                     │    │
│  │                                                  │    │
│  │  ┌──────────────────────────────────────┐     │    │
│  │  │   minecraft-ollama-server            │     │    │
│  │  │   (Ollama Container)                 │     │    │
│  │  │                                       │     │    │
│  │  │  ┌─────────────────────────────┐    │     │    │
│  │  │  │  Ollama Service             │    │     │    │
│  │  │  │  Port: 11434                │    │     │    │
│  │  │  │                              │    │     │    │
│  │  │  │  - API Endpoints             │    │     │    │
│  │  │  │  - Model Management          │    │     │    │
│  │  │  │  - LLM Inference             │    │     │    │
│  │  │  └─────────────────────────────┘    │     │    │
│  │  │                                       │     │    │
│  │  │  Volumes:                            │     │    │
│  │  │  - ollama_data:/root/.ollama        │     │    │
│  │  │  - ./config:/app/config (read-only) │     │    │
│  │  │  - ./data:/app/data                 │     │    │
│  │  └──────────────────────────────────────┘     │    │
│  └────────────────────────────────────────────────┘    │
│                                                          │
│  Port Mapping: localhost:11434 → container:11434       │
└─────────────────────────────────────────────────────────┘
           │                              ▲
           │                              │
           ▼                              │
┌─────────────────────────────────────────────────────────┐
│            Minecraft Mod Client                         │
│                                                          │
│  - HTTP Requests to localhost:11434                     │
│  - Chat API integration                                 │
│  - Generate API for NPC dialogues                       │
└─────────────────────────────────────────────────────────┘
```

## Components

### 1. Docker Container (minecraft-ollama-server)
- **Base Image**: `ollama/ollama:latest`
- **Purpose**: Runs the Ollama LLM service
- **Port**: 11434 (HTTP API)
- **Health Check**: Periodic check of `/api/tags` endpoint

### 2. Persistent Storage (ollama_data volume)
- **Purpose**: Store downloaded LLM models
- **Location**: Docker volume (managed by Docker)
- **Size**: Varies based on models (typically 2-10GB)
- **Persistence**: Survives container restarts and rebuilds

### 3. Configuration Directory (./config)
- **Purpose**: Store environment variables and settings
- **Mount Type**: Read-only bind mount
- **Files**: 
  - `ollama.env.example` - Template configuration

### 4. Data Directory (./data)
- **Purpose**: Shared data between host and container
- **Mount Type**: Read-write bind mount
- **Use Cases**: 
  - Shared context files
  - Pre-processed game data
  - Temporary files

### 5. Scripts Directory (./scripts)
- **Purpose**: Automation and setup utilities
- **Scripts**:
  - `setup-models.sh` - Install demo models (Bash)
  - `setup-models.ps1` - Install demo models (PowerShell)
  - `test-demo.sh` - Run demo tests

## Data Flow

### Model Installation Flow
```
User executes setup-models.sh
    ↓
Script connects to Docker container
    ↓
Container pulls model from Ollama registry
    ↓
Model stored in ollama_data volume
    ↓
Model available for API requests
```

### API Request Flow
```
Minecraft Mod
    ↓
HTTP POST to localhost:11434/api/generate
    ↓
Docker port forwarding to container
    ↓
Ollama service processes request
    ↓
LLM generates response
    ↓
Response returned as JSON
    ↓
Minecraft Mod displays to player
```

## Recommended Models

### For Demo (Lightweight & Fast)
1. **llama3.2:1b** (1.3GB)
   - Fastest response time
   - Good for real-time interactions
   - Suitable for villager conversations

2. **phi3:mini** (2.3GB)
   - Optimized for chat
   - Better context understanding
   - Good for complex dialogues

### For Production (Better Quality)
1. **llama3.2:3b** (2GB)
   - Better response quality
   - Still reasonably fast
   
2. **mistral** (4.1GB)
   - High-quality responses
   - Good general-purpose model

## Network Architecture

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│              │         │              │         │              │
│  Developer   │──LAN───▶│  Ada Server  │◀──API──│  Minecraft   │
│  Machine     │         │  (Docker)    │         │  Client      │
│              │         │              │         │              │
└──────────────┘         └──────────────┘         └──────────────┘
                                │
                                │ Internet
                                ▼
                         ┌──────────────┐
                         │   Ollama     │
                         │   Registry   │
                         │  (Models)    │
                         └──────────────┘
```

## Security Considerations

1. **API Exposure**: The Ollama API is exposed only on localhost by default
2. **Read-only Config**: Configuration directory is mounted read-only
3. **Volume Isolation**: Model data is isolated in Docker volumes
4. **No Authentication**: Ollama API has no authentication by default (suitable for local dev)

## Performance Characteristics

### Resource Requirements
- **CPU**: 2-4 cores recommended
- **RAM**: 4-8GB minimum (depends on model size)
- **Disk**: 10-20GB for models and cache
- **Network**: Required for initial model download

### Response Times (Approximate)
- **llama3.2:1b**: 50-200ms per token
- **phi3:mini**: 100-300ms per token
- **mistral**: 200-500ms per token

*Note: Times vary based on hardware, prompt length, and system load*

## Scaling Considerations

For production deployment, consider:

1. **Multiple Model Instances**: Run multiple containers with different models
2. **Load Balancing**: Distribute requests across containers
3. **GPU Acceleration**: Use NVIDIA GPUs for faster inference
4. **Model Caching**: Pre-warm models on startup
5. **Request Queuing**: Implement queue for concurrent requests

## Troubleshooting Architecture

```
Issue Detected
    ↓
Check docker-compose logs
    ↓
Container Status OK?
    ├─ NO → Restart container
    │       Check port conflicts
    │       Check Docker resources
    └─ YES → Check API endpoint
            ↓
        API Responding?
            ├─ NO → Check firewall
            │       Check container logs
            └─ YES → Check model availability
                    ↓
                Model Loaded?
                    ├─ NO → Pull model
                    └─ YES → Test with curl
```

## Future Enhancements

1. **Monitoring Dashboard**: Add Grafana/Prometheus for metrics
2. **Model Management UI**: Web interface for model selection
3. **Caching Layer**: Redis for frequent responses
4. **Auto-scaling**: Scale based on request volume
5. **Multi-model Support**: Route requests to appropriate models
6. **Context Management**: Persistent conversation context
