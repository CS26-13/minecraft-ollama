.PHONY: help start stop restart logs status models test clean backup

# Default target - show help
help:
	@echo "Minecraft Ollama Server - Makefile Commands"
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  start      - Start the Ollama server"
	@echo "  stop       - Stop the Ollama server"
	@echo "  restart    - Restart the Ollama server"
	@echo "  logs       - View server logs (follow mode)"
	@echo "  status     - Check server status"
	@echo "  models     - Install demo models"
	@echo "  test       - Run demo tests"
	@echo "  clean      - Stop server and remove containers (keeps volumes)"
	@echo "  clean-all  - Remove everything including models (WARNING)"
	@echo "  backup     - Backup models to ./backup directory"
	@echo "  shell      - Open shell in the container"
	@echo ""

# Start the server
start:
	@echo "Starting Ollama server..."
	@docker-compose up -d
	@echo "Waiting for server to be ready..."
	@sleep 5
	@docker-compose ps
	@echo "Server started! API available at http://localhost:11434"

# Stop the server
stop:
	@echo "Stopping Ollama server..."
	@docker-compose down
	@echo "Server stopped."

# Restart the server
restart:
	@echo "Restarting Ollama server..."
	@docker-compose restart
	@echo "Server restarted."

# View logs
logs:
	@docker-compose logs -f

# Check status
status:
	@echo "=== Container Status ==="
	@docker-compose ps
	@echo ""
	@echo "=== Installed Models ==="
	@docker exec minecraft-ollama-server ollama list || echo "Server not running"
	@echo ""
	@echo "=== API Health ==="
	@curl -s http://localhost:11434/api/tags > /dev/null 2>&1 && echo "✓ API is responding" || echo "✗ API is not responding"

# Install demo models
models:
	@echo "Installing demo models..."
	@chmod +x scripts/setup-models.sh
	@./scripts/setup-models.sh

# Run tests
test:
	@echo "Running demo tests..."
	@chmod +x scripts/test-demo.sh
	@./scripts/test-demo.sh

# Clean up (keep volumes)
clean:
	@echo "Cleaning up containers..."
	@docker-compose down
	@echo "Cleanup complete. Models are preserved."

# Clean everything (including models)
clean-all:
	@echo "WARNING: This will delete all models!"
	@read -p "Are you sure? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		docker-compose down -v; \
		echo "Full cleanup complete."; \
	else \
		echo "Cancelled."; \
	fi

# Backup models
backup:
	@echo "Creating backup..."
	@mkdir -p backup
	@docker run --rm \
		-v minecraft-ollama_ollama_data:/data \
		-v $$(pwd)/backup:/backup \
		alpine tar czf /backup/models-backup-$$(date +%Y%m%d-%H%M%S).tar.gz -C /data .
	@echo "Backup created in ./backup/"
	@ls -lh backup/

# Open shell in container
shell:
	@docker exec -it minecraft-ollama-server /bin/bash

# Development mode - start with logs
dev:
	@docker-compose up
