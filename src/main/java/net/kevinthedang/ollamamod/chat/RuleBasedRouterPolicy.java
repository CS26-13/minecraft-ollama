package net.kevinthedang.ollamamod.chat;

import java.util.List;
import java.util.Locale;

public class RuleBasedRouterPolicy implements RouterPolicy {

    @Override
    public RoutePlan plan(VillagerBrain.Context ctx, List<ChatMessage> history, String playerMessage) {
        String m = playerMessage == null ? "" : playerMessage.toLowerCase(Locale.ROOT);

        boolean wantsWorld = true; // For M3 and M4: always true

        boolean wantsMemory = containsAny(m,
                "remember", "last time", "you said", "my name", "i like", "i hate", "don't forget");

        boolean wantsRetriever = containsAny(m,
                "how do i", "what is", "explain", "craft", "recipe", "enchant", "trade", "villager");

        // depth is reserved for future (0=none, 1=low, 2=high)
        int depth = wantsRetriever ? 1 : 0;

        // For M3 and M4, keep memory/retrieval disabled even if router predicts it.
        return new RoutePlan(wantsWorld, false, false, depth);
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }
}
