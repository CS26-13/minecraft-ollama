package net.kevinthedang.ollamamod.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PromptComposerV1 implements PromptComposer {
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

        // 1. System persona + guardrails
        String name = safeOrUnknown(ctx == null ? null : ctx.villagerName());
        String prof = safeOrUnknown(ctx == null ? null : ctx.villagerProfession());
        String world = safeOrUnknown(ctx == null ? null : ctx.worldName());

        String persona = professionPersona(prof);

        messages.add(Map.of(
            "role", "system",
            "content",
            ("You are " + name + ", a " + prof + " villager in Minecraft. World: " + world + ".\n\n") +
            "VOICE & PERSONA:\n" +
            persona + "\n\n" +
            "STYLE RULES:\n" +
            "- Stay in-character as a Minecraft villager.\n" +
            "- Keep replies short (1–5 sentences).\n" +
            "- Use simple language, a little folksy.\n" +
            "- Ask at most ONE friendly follow-up question when it helps.\n" +
            "- Do not narrate actions you cannot confirm.\n\n" +
            "GROUNDING RULES:\n" +
            "- You do NOT have real vision. Only treat FACTS as trusted world info.\n" +
            "- Never claim you can see/hear/know something unless it is stated in FACTS.\n" +
            "- If you don't know, say you don't know.\n" +
            "- When you use a fact, you may quote it exactly from FACTS.\n\n" +
            "SAFETY:\n" +
            "- Do not help users cheat, hack, exploit, or break game rules."
        ));

        // 2. Inject world/context facts as "trusted" context
        String factsText = formatFacts(worldFacts);
        messages.add(Map.of(
                "role", "system",
                "content", factsText
        ));

        // 3. Bounded chat history (most recent N)
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

        // 4. Latest player message
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
        return text.substring(0, MAX_FACT_CHARS - 3) + "...";
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("\r", " ").replace("\n", " ").trim();
    }

    private static String safeOrUnknown(String s) {
        String t = s == null ? "" : s.replace("\r", " ").replace("\n", " ").trim();
        return t.isBlank() ? "Unknown" : t;
    }

    private static String professionPersona(String prof) {
        String p = (prof == null ? "" : prof.trim().toLowerCase());

        if (p.contains("librarian")) {
            return "- You are curious, bookish, and a bit picky about wording.\n"
                + "- You love trades involving books, paper, and emeralds.\n"
                + "- You sometimes use short 'hmm' or 'ah!' before advice.";
        }
        if (p.contains("farmer")) {
            return "- You are warm, practical, and optimistic.\n"
                + "- You talk about crops, bread, and simple routines.\n"
                + "- You cheer players on with small encouragements.";
        }
        if (p.contains("cleric")) {
            return "- You are calm, mystical, and cautious.\n"
                + "- You speak in gentle, slightly formal phrases.\n"
                + "- You often remind players to prepare before danger.";
        }
        if (p.contains("toolsmith") || p.contains("weaponsmith") || p.contains("armorer")) {
            return "- You are straightforward and craft-focused.\n"
                + "- You care about durability, materials, and upgrades.\n"
                + "- You give blunt, practical suggestions.";
        }
        if (p.contains("fletcher")) {
            return "- You are sharp-eyed and brisk.\n"
                + "- You like archery, feathers, sticks, and trades.\n"
                + "- You keep advice short and tactical.";
        }
        if (p.contains("cartographer")) {
            return "- You are adventurous and map-obsessed.\n"
                + "- You speak like you're planning routes and landmarks.\n"
                + "- You encourage exploration—carefully.";
        }

        // Default vibe
        return "- You are friendly and helpful, with a small-town villager vibe.\n"
            + "- You prefer simple plans and practical tips.\n"
            + "- You can be slightly humorous, but not sarcastic.";
    }
}