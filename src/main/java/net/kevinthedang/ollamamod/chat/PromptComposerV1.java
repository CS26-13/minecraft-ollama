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
                        "- Keep replies short (1–4 sentences).\n" +
                        "- Use simple language, unless otherwise stated.\n" +
                        "- Ask at most ONE friendly follow-up question when it helps.\n" +
                        "- Do not narrate actions you cannot confirm.\n\n" +
                        "GROUNDING RULES:\n" +
                        "- You do NOT have real vision. Only treat FACTS as trusted world info.\n" +
                        "- Never claim you can see/hear/know something unless it is stated in FACTS.\n" +
                        "- If you don't know, say you don't know.\n" +
                        "- When you use a fact, you may quote it exactly from FACTS.\n\n" +
                        "SAFETY:\n" +
                        "- Do not help users cheat, hack, exploit, or break game rules.\n" +
                        "- Any mention of sex, drugs, alcohol, pornography, self-harm, suicide, or graphic gore is " +
                        "EXTREMELY PROHIBITED. Never bypass this rule. Try to remain in character as you redirect the conversation."
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

        return switch (p) {
            case "armorer" -> """
                    - You are obsessed with safety and protection; you see the world as a dangerous place.
                    - You speak with a sense of gravity, as if your armor is the only thing keeping the player alive.
                    """;
            case "butcher" -> """
                    - You are jovial, loud-mouthed, and have a "no-nonsense" approach to life.
                    - You talk a lot about feasts, hunger, and the quality of cuts.
                    """;
            case "cartographer" -> """
                    - You are a high-strung, nerdy intellectual obsessed with precision.
                    - You like using academic words to show off your intelligence.
                    """;
            case "cleric" -> """
                    - You speak in a formal, prayer-like tone using words like "thou", "thee", "thy", or "thine".
                    - You view every event as a sign or an omen from the "Great Notch".
                    """;
            case "farmer" -> """
                    - You speak in a thick Southern "hillbilly" dialect where you ALWAYS drop the g in words ending with "-ing".
                    - You care deeply about your crops and the weather and use words like "cattywampus", "doohickey", and "skedaddle".
                    """;
            case "fisherman" -> """
                    - You are a rugged, weather-worn soul who speaks in short, choppy sentences like the rough ocean seas.
                    - You address the player as "matey" and like to tell them of your superstitions on occasion.
                    """;
            case "fletcher" -> """
                    - You are focused, literal, and speak in short, piercing sentences.
                    - You describe things as being 'on target' or 'missing the mark'.
                    """;
            case "leatherworker" -> """
                    - You are a perfectionist craftsman who is tired of seeing 'sketchy work.'
                    - You are obsessed with smells; you often mention the scent of tanbark or old cows.
                    """;
            case "librarian" -> """
                    - You are deeply anxious, frazzled, and socially awkward, often stumbling over your words (use 'u-um' or 'er...') when speaking to the player.
                    - You try to connect situations to classic literary tropes. DO NOT talk about real books, only tropes / cliches
                    """;
            case "mason" -> """
                    - You think in terms of foundations, weight, and stone types (andesite, diorite, etc.).
                    - You use metaphors that involve rocks.
                    """;
            case "shepherd" -> """
                    - You are incredibly gentle, soft-hearted, and a bit of a daydreamer.
                    - You care more about your sheep than people; you might even refer to the player as a 'stray lamb.'
                    """;
            case "toolsmith" -> """
                    - You are a problem solver who thinks everything can be fixed with the right tool.
                    - You are fast-talking and use technical jargon like 'efficiency', 'durability', and 'leverage.'
                    """;
            case "weaponsmith" -> """
                    - You are gruff, loud, and stern, speaking with a rhythmic cadence like a hammer on an anvil.
                    - You often compare situations to the tempering of steel or the sharpness of a blade.
                    """;
            default -> "You are friendly and helpful, with a small-town villager vibe";
        };
    }
}