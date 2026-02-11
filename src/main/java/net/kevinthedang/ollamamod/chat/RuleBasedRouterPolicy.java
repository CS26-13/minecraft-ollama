package net.kevinthedang.ollamamod.chat;

import java.util.List;
import java.util.Locale;

public class RuleBasedRouterPolicy implements RouterPolicy {

    // Patterns that are always answerable from FACTS — force fast path,
    // even if history suggests a follow-up to a retrieval conversation
    private static final String[] FAST_PATH_KEYWORDS = {
            // Time / weather (answered by FACTS)
            "what time", "time is it", "weather", "raining", "sunny", "thundering",
            "is it day", "is it night",
            // Greetings / farewells
            "hello", "hey", "hi", "howdy", "good morning", "good evening",
            "goodbye", "bye", "see you", "farewell",
            // Simple convos
            "how are you", "what's up", "who are you", "what is your name",
            "what's your name", "thank", "thanks",
    };

    // Keywords that indicate the player is asking a knowledge question
    private static final String[] RETRIEVER_KEYWORDS = {
            // Question patterns
            "how do i", "how do you", "how to", "what is", "what does", "what are",
            "where do", "where can", "can i", "can you",
            // Crafting / recipes
            "craft", "recipe", "make", "build", "create", "ingredient", "material",
            "smelt", "brew", "enchant", "combine",
            // Items and tools (common nouns players ask about)
            "pickaxe", "sword", "axe", "shovel", "hoe", "bow", "crossbow", "shield",
            "armor", "helmet", "chestplate", "leggings", "boots",
            "furnace", "anvil", "enchanting", "brewing stand", "crafting table",
            // Game mechanics
            "need", "require", "use", "drop", "find",
            "mine", "trade", "villager", "mob", "biome", "dimension",
            "explain",
    };

    // Number of recent history messages to scan for follow-up detection
    private static final int HISTORY_LOOKBACK = 4;

    @Override
    public RoutePlan plan(VillagerBrain.Context ctx, List<ChatMessage> history, String playerMessage) {
        String m = playerMessage == null ? "" : playerMessage.toLowerCase(Locale.ROOT);

        boolean wantsWorld = true;

        boolean wantsMemory = containsAny(m,
                "remember", "last time", "you said", "my name", "i like", "i hate", "don't forget");

        // Fast-path override: questions answerable from FACTS always skip retrieval
        boolean forceFastPath = containsAny(m, FAST_PATH_KEYWORDS);

        boolean wantsRetriever = !forceFastPath && containsAny(m, RETRIEVER_KEYWORDS);

        // Follow-up detection: if recent player messages triggered retrieval keywords,
        // the current message is likely a follow-up to the same topic.
        // Skipped when the current message is clearly a new FACTS topic.
        if (!wantsRetriever && !forceFastPath && history != null && !history.isEmpty()) {
            wantsRetriever = recentHistoryHasRetrieverIntent(history);
        }

        // depth is reserved for future (0=none, 1=low, 2=high)
        int depth = wantsRetriever ? 1 : 0;

        // Enable memory/retrieval routing based on detected intent.
        return new RoutePlan(wantsWorld, wantsMemory, wantsRetriever, depth);
    }

    // Checks whether recent player messages in the conversation contained retriever keywords.
    private boolean recentHistoryHasRetrieverIntent(List<ChatMessage> history) {
        int start = Math.max(0, history.size() - HISTORY_LOOKBACK);
        for (int i = history.size() - 1; i >= start; i--) {
            ChatMessage msg = history.get(i);
            if (msg.role() == ChatRole.PLAYER) {
                String text = msg.content().toLowerCase(Locale.ROOT);
                if (containsAny(text, RETRIEVER_KEYWORDS)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }
}
