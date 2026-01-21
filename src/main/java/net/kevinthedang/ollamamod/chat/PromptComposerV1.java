package net.kevinthedang.ollamamod.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * M3 prompt composer:
 * - Adds persona/system rules
 * - Injects FACTS (>=3) as a dedicated system message
 * - Includes a bounded amount of chat history
 */
public class PromptComposerV1 implements PromptComposer {

    // Simple budget controls (M3: character-based, not token-based)
    private static final int MAX_HISTORY_MESSAGES = 12;
    private static final int MAX_FACT_CHARS = 1200;

    @Override
    public List<Map<String, String>> buildMessages(
            VillagerBrain.Context ctx,
            List<ChatMessage> history,
            String playerMessage,
            WorldFactBundle worldFacts
    ) {
        List<Map<String, String>> messages = new ArrayList<>();

        // 1) System persona + guardrails
        messages.add(Map.of(
                "role", "system",
                "content",
                "You are a Minecraft character talking to a player. " +
                "Stay in-character. Keep replies short (1-5 sentences). " +
                "Use simple language. " +
                "Do not claim you can see things unless it is stated in FACTS. " +
                "If you don't know, say you don't know. " +
                "When you use a fact, you may quote it exactly from FACTS. " +
                "Do not help users cheat, hack, exploit, or break game rules."
        ));

        // 2) Inject world/context facts as "trusted" context
        String factsText = formatFacts(worldFacts);
        messages.add(Map.of(
                "role", "system",
                "content", factsText
        ));

        // 3) Bounded chat history (most recent N)
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
            for (ChatMessage msg : history.subList(start, history.size())) {
                String role = switch (msg.role()) {
                    case PLAYER -> "user";
                    case VILLAGER -> "assistant";
                    case SYSTEM -> "system";
                };
                messages.add(Map.of(
                        "role", role,
                        "content", msg.content()
                ));
            }
        }

        // 4) Latest player message
        messages.add(Map.of(
                "role", "user",
                "content", playerMessage == null ? "" : playerMessage
        ));

        return messages;
    }

    private static String formatFacts(WorldFactBundle bundle) {
        if (bundle == null || bundle.facts() == null || bundle.facts().isEmpty()) {
            return "FACTS:\n- (none)";
        }
        String joined = bundle.facts().stream()
                .map(f -> "- " + (f == null ? "" : safe(f.factText())))
                .collect(Collectors.joining("\n"));

        String text = "FACTS (trusted context):\n" + joined +
                "\n\nINSTRUCTIONS:\n" +
                "- Treat FACTS as the only reliable world context.\n" +
                "- Do not invent new world facts.\n" +
                "- If helpful, quote a fact word-for-word.\n";

        if (text.length() <= MAX_FACT_CHARS) return text;
        // Hard clamp for Phase 1
        return text.substring(0, MAX_FACT_CHARS - 3) + "...";
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("\r", " ").replace("\n", " ").trim();
    }
}
