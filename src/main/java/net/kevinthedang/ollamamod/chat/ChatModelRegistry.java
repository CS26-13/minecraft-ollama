package net.kevinthedang.ollamamod.chat;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// Registry of known model configurations.
// Registered entries carry sampling params and capability flags.
// Unregistered models work fine — they just get no extra options.
public final class ChatModelRegistry {

	private static final Map<String, ChatModelConfig> BY_OLLAMA_ID = new LinkedHashMap<>();
	private static final Map<String, ChatModelConfig> BY_LOGICAL_NAME = new LinkedHashMap<>();

	static {
		register(new ChatModelConfig(
			"granite4", "granite4:latest",
			null, null, null, null, null, true
		));
		register(new ChatModelConfig(
			"gemma4", "gemma4:e4b",
			1.0, 0.95, 64, 8192, false, true
		));
		register(new ChatModelConfig(
			"gemma4", "gemma4:e2b",
			1.0, 0.95, 64, 8192, false, true
		));
		register(new ChatModelConfig(
			"minimax", "minimax-m2.5:cloud",
			null, null, null, null, null, true
		));
	}

	private static void register(ChatModelConfig config) {
		BY_OLLAMA_ID.put(config.ollamaModelId(), config);
		BY_LOGICAL_NAME.put(config.logicalName(), config);
	}

	// Look up by the exact Ollama model ID (e.g., "granite4:latest").
	public static Optional<ChatModelConfig> forOllamaId(String ollamaModelId) {
		return Optional.ofNullable(BY_OLLAMA_ID.get(ollamaModelId));
	}

	// Look up by logical name (e.g., "granite4").
	public static Optional<ChatModelConfig> forLogicalName(String logicalName) {
		return Optional.ofNullable(BY_LOGICAL_NAME.get(logicalName));
	}

	// Resolve a string that may be either a logical name or an Ollama model ID.
	// Tries logical name first, then Ollama ID, then returns empty.
	public static Optional<ChatModelConfig> resolve(String nameOrId) {
		Optional<ChatModelConfig> config = forLogicalName(nameOrId);
		if (config.isPresent()) return config;
		return forOllamaId(nameOrId);
	}

	// All registered configs, in insertion order.
	public static Collection<ChatModelConfig> all() {
		return Collections.unmodifiableCollection(BY_OLLAMA_ID.values());
	}

	private ChatModelRegistry() {}
}
