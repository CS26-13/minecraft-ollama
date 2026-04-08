# Chat Model Configuration

This mod supports multiple Ollama chat models via a model registry. Models can be assigned to two roles:

- **Chat Model** (`chatModel`) — used for simple, low-effort conversations (greetings, weather, etc.)
- **Tool Model** (`toolModel`) — used for higher-effort conversations that may involve tool calling (knowledge search, memory recall)

## Registered Models

| Logical Name | Ollama Model ID      | Temperature | top_p | top_k | num_ctx | Native Tools | Notes                                    |
|--------------|----------------------|-------------|-------|-------|---------|--------------|------------------------------------------|
| `granite4`   | `granite4:latest`    | default     | default | default | default | Yes          | Default chat model                       |
| `gemma4`     | `gemma4:e4b`         | 1.0         | 0.95  | 64    | 8192    | Yes          | Google recommended sampling settings     |
| `minimax`    | `minimax-m2.5:cloud` | default     | default | default | default | Yes          | Default tool model                       |

Models marked "default" use Ollama's built-in defaults (no explicit sampling params sent in the request).

## Switching Models

### Option 1: Environment Variables (recommended for A/B testing)

```bash
# Use gemma4 as the chat model (granite4 remains default if unset)
OLLAMA_CHAT_MODEL=gemma4:e4b ./gradlew runClient

# Use gemma4 for both roles
OLLAMA_CHAT_MODEL=gemma4:e4b OLLAMA_TOOL_MODEL=gemma4:e4b ./gradlew runClient
```

Environment variables take precedence over the Forge config file.

### Option 2: In-Game `/model` Command

In the villager chat screen, type `/model` to:
1. See all models available on your Ollama instance
2. Select a model and assign it to the Chat Model or Tool Model role
3. Changes are persisted to the Forge config file

### Option 3: Forge Config File

Edit `config/ollamamod-common.toml` (created on first run):

```toml
chatModel = "granite4:latest"
toolModel = "minimax-m2.5:cloud"
```

## Verification Script

Test that a model works correctly with the chat API and tool calling:

```bash
# Test the default model
./scripts/verify_chat_model.sh

# Test a specific model
./scripts/verify_chat_model.sh gemma4:e4b

# Test against a remote Ollama instance
./scripts/verify_chat_model.sh gemma4:e4b http://192.168.1.100:11434
```

The script runs two checks:
1. **Basic chat** — sends a system + user message, verifies a non-empty response with no leaked special tokens
2. **Tool calling** — sends a tool definition and a triggering message, verifies `message.tool_calls` is populated

## Adding a New Model

1. Add a `register()` call in `ChatModelRegistry.java`:

```java
register(new ChatModelConfig(
    "mymodel",           // logical name
    "mymodel:7b",        // Ollama model ID (must match `ollama list` output)
    0.7,                 // temperature (null = use Ollama default)
    0.9,                 // top_p (null = use default)
    40,                  // top_k (null = use default)
    4096,                // num_ctx (null = use default)
    true                 // supports native tool calling
));
```

2. Run the verification script: `./scripts/verify_chat_model.sh mymodel:7b`
3. Assign it to a role via env var or in-game `/model` command

Unregistered models (not in the registry) still work — they just use Ollama's default sampling parameters.
