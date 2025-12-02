package net.kevinthedang.ollamamod.chat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// TODO: Implement LLM Bridge here

public interface VillagerBrain {

    record Context(
            UUID conversationId,
            String villagerName,
            String villagerProfession,
            String worldName
    ) { }

    /**
     * Ask the brain for a reply. Must be non-blocking.
     *
     * @param context        basic villager/world info
     * @param history        recent chat history
     * @param playerMessage  latest player message
     * @return future with the villager's full reply text
     */
    CompletableFuture<String> getReply(
            Context context,
            List<ChatMessage> history,
            String playerMessage
    );

    default boolean isHealthy() {
        return true;
    }
}
