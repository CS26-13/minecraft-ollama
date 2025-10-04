# Project Structure Overview

This document provides a visual overview of the complete project structure after the Ada Server setup.

## Directory Tree

```
minecraft-ollama/
├── .github/                          # GitHub configuration
│   ├── CONTRIBUTING.md              # Contribution guidelines
│   └── style.md                     # Code style guide
│
├── server/                          # ⭐ Ada Server Setup (NEW)
│   ├── config/                      # Server configuration
│   │   ├── .gitkeep                # Preserve empty directory
│   │   └── ollama.env.example      # Environment template
│   │
│   ├── data/                        # Shared data directory
│   │   └── .gitkeep                # Preserve empty directory
│   │
│   ├── scripts/                     # Utility scripts
│   │   ├── setup-models.sh         # Model installation (Bash)
│   │   ├── setup-models.ps1        # Model installation (PowerShell)
│   │   └── test-demo.sh            # Demo testing script
│   │
│   ├── ARCHITECTURE.md             # System architecture docs
│   ├── Dockerfile                  # Ollama container config
│   ├── docker-compose.yml          # Docker orchestration
│   ├── Makefile                    # Common operations
│   ├── QUICK_REFERENCE.md          # Command reference
│   └── README.md                   # Server directory guide
│
├── src/                            # Minecraft mod source code
│   └── main/
│       ├── java/                   # Java source files
│       │   └── net/kevinthedang/ollamamod/
│       │       ├── OllamaMod.java  # Main mod class
│       │       └── Config.java     # Configuration
│       │
│       └── resources/              # Resources (configs, assets)
│
├── imgs/                           # Project images/icons
│
├── .env.example                    # ⭐ Project config template (NEW)
├── .gitignore                      # Git ignore rules (updated)
├── LICENSE                         # GPL v3.0 license
├── README.md                       # Main project README (updated)
├── SERVER_SETUP.md                 # ⭐ Server setup guide (NEW)
├── build.gradle                    # Gradle build configuration
├── gradle.properties               # Gradle properties
├── gradlew                         # Gradle wrapper (Unix)
├── gradlew.bat                     # Gradle wrapper (Windows)
└── settings.gradle                 # Gradle settings
```

## File Categories

### 🐳 Docker & Container Files
- `server/Dockerfile` - Base image and container setup
- `server/docker-compose.yml` - Multi-container orchestration
- `server/Makefile` - Automation commands

### 📝 Configuration Files
- `server/config/ollama.env.example` - Ollama environment variables
- `.env.example` - Project-wide configuration
- `src/main/java/*/Config.java` - Mod configuration

### 🔧 Scripts & Automation
- `server/scripts/setup-models.sh` - Bash model installer
- `server/scripts/setup-models.ps1` - PowerShell model installer
- `server/scripts/test-demo.sh` - API testing script

### 📚 Documentation Files
- `SERVER_SETUP.md` - Complete setup instructions
- `server/README.md` - Server directory overview
- `server/ARCHITECTURE.md` - System architecture
- `server/QUICK_REFERENCE.md` - Command reference
- `README.md` - Main project documentation
- `.github/CONTRIBUTING.md` - Contribution guide
- `.github/style.md` - Style guidelines

### ☕ Minecraft Mod Files
- `src/main/java/` - Java source code
- `src/main/resources/` - Mod resources
- `build.gradle` - Build configuration
- `gradle.properties` - Build properties

## New Files Added in This Setup

### Essential Server Files (5)
1. `server/Dockerfile` - Container image definition
2. `server/docker-compose.yml` - Service orchestration
3. `server/config/ollama.env.example` - Config template
4. `server/scripts/setup-models.sh` - Model installer
5. `server/Makefile` - Automation tool

### Documentation Files (5)
1. `SERVER_SETUP.md` - Main setup guide
2. `server/README.md` - Quick start guide
3. `server/ARCHITECTURE.md` - Architecture details
4. `server/QUICK_REFERENCE.md` - Command reference
5. `server/PROJECT_STRUCTURE.md` - This file

### Supporting Files (4)
1. `server/scripts/setup-models.ps1` - Windows installer
2. `server/scripts/test-demo.sh` - Demo testing
3. `.env.example` - Project config template
4. `server/config/.gitkeep` - Directory preservation
5. `server/data/.gitkeep` - Directory preservation

### Updated Files (2)
1. `.gitignore` - Added server exclusions
2. `README.md` - Added server setup section

**Total: 17 new files + 2 updated files**

## Size Reference

| File | Size | Purpose |
|------|------|---------|
| SERVER_SETUP.md | 8.5KB | Comprehensive setup guide |
| ARCHITECTURE.md | 7.4KB | System architecture |
| QUICK_REFERENCE.md | 6.3KB | Command reference |
| Makefile | 2.8KB | Automation |
| server/README.md | 2.2KB | Quick start |
| test-demo.sh | 1.8KB | Demo testing |
| setup-models.ps1 | 1.5KB | Windows installer |
| setup-models.sh | 1.1KB | Bash installer |
| docker-compose.yml | 700B | Docker config |
| Dockerfile | 562B | Container image |
| ollama.env.example | 497B | Environment vars |
| .env.example | 355B | Project config |

## Workflow Paths

### Developer Setup Workflow
```
1. Clone repository
2. Navigate to server/
3. Run: make start
4. Run: make models
5. Run: make test
6. Start developing!
```

### Files Touched in Workflow
```
Step 1: README.md → SERVER_SETUP.md
Step 2: cd server/
Step 3: Makefile → docker-compose.yml → Dockerfile
Step 4: Makefile → scripts/setup-models.sh
Step 5: Makefile → scripts/test-demo.sh
Step 6: src/main/java/...
```

## Git Tracking

### Tracked Files (Committed)
- All documentation (*.md)
- All scripts (scripts/*.sh, scripts/*.ps1)
- All configuration templates (*.example)
- Docker files (Dockerfile, docker-compose.yml)
- Makefile
- Directory markers (.gitkeep)

### Ignored Files (Not Committed)
- `server/data/*` - Runtime data (except .gitkeep)
- `server/config/ollama.env` - User config
- `server/backup/` - Backup files
- `.env` - User environment
- `.docker/` - Docker cache
- `*.log` - Log files

## Quick Access Map

Need to...
- **Start server?** → `server/Makefile` or `server/docker-compose.yml`
- **Install models?** → `server/scripts/setup-models.sh`
- **Test API?** → `server/scripts/test-demo.sh`
- **Configure?** → `server/config/ollama.env.example`
- **Troubleshoot?** → `SERVER_SETUP.md` (Troubleshooting section)
- **Learn commands?** → `server/QUICK_REFERENCE.md`
- **Understand architecture?** → `server/ARCHITECTURE.md`
- **Quick start?** → `server/README.md`

## Integration Points

```
Minecraft Mod (src/main/java/)
        ↓
    HTTP API Client
        ↓
    localhost:11434
        ↓
Docker Container (server/docker-compose.yml)
        ↓
    Ollama Service
        ↓
    LLM Models (ollama_data volume)
```

## Platform Compatibility

| Component | Mac | Windows | Linux |
|-----------|-----|---------|-------|
| Docker Setup | ✅ | ✅ | ✅ |
| Bash Scripts | ✅ | ⚠️* | ✅ |
| PowerShell Scripts | ⚠️** | ✅ | ⚠️** |
| Makefile | ✅ | ⚠️*** | ✅ |
| Minecraft Mod | ✅ | ✅ | ✅ |

\* Use Git Bash or WSL2  
\*\* Can install PowerShell Core  
\*\*\* Use WSL2 or Git Bash

## Maintenance

### Regular Updates
- Pull new Ollama images: `docker pull ollama/ollama:latest`
- Update models: `docker exec minecraft-ollama-server ollama pull <model>`
- Backup models: `make backup`

### Cleanup
- Remove unused containers: `docker system prune`
- Free disk space: `docker volume ls` → `docker volume rm`
- Clear logs: `docker-compose down && docker-compose up -d`

## Support Resources

1. **Primary**: SERVER_SETUP.md
2. **Commands**: server/QUICK_REFERENCE.md
3. **Architecture**: server/ARCHITECTURE.md
4. **Issues**: GitHub Issues page
5. **Ollama Docs**: https://github.com/ollama/ollama

---

*This structure supports the goal of creating a cross-platform development environment for integrating Ollama LLMs into Minecraft.*
