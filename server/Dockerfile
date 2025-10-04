# Dockerfile for Ollama Server Setup
# This provides a cross-platform development environment for the Minecraft Ollama project

FROM ollama/ollama:latest

# Set working directory
WORKDIR /app

# Create directories for Ollama models and configuration
RUN mkdir -p /root/.ollama/models

# Expose Ollama API port
EXPOSE 11434

# Health check to ensure Ollama is running
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:11434/api/tags || exit 1

# Start Ollama service
ENTRYPOINT ["/bin/ollama"]
CMD ["serve"]
