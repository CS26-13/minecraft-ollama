package net.kevinthedang.ollamamod.chat;

// Immutable configuration for a registered Ollama chat model.
public record ChatModelConfig(
	String logicalName,
	String ollamaModelId,
	Double temperature,
	Double topP,
	Integer topK,
	Integer numCtx,
	Boolean think,
	boolean supportsNativeTools
) {
	// Returns true if any sampling/options params are explicitly set.
	public boolean hasOptions() {
		return temperature != null || topP != null || topK != null || numCtx != null;
	}
}
