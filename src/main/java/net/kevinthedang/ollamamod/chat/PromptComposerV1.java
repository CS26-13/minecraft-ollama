package net.kevinthedang.ollamamod.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PromptComposerV1 implements PromptComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PromptComposerV1.class);
    private static final int MAX_HISTORY_MESSAGES = 12;
    private static final int MAX_FACT_CHARS = 1800;
    private static final int MAX_SKILL_CHARS = 800;
    private static final PromptLoader LOADER = PromptLoader.getInstance();

    @Override
    public List<Map<String, String>> buildMessages(
            VillagerBrain.Context ctx,
            List<ChatMessage> history,
            String playerMessage,
            WorldFactBundle worldFacts
    ) {
        List<Map<String, String>> messages = new ArrayList<>();

        // 1. System prompt: base identity + resolved persona + conditionally injected skills
        String name  = safeOrUnknown(ctx == null ? null : ctx.villagerName());
        String prof  = safeOrUnknown(ctx == null ? null : ctx.villagerProfession());
        String world = safeOrUnknown(ctx == null ? null : ctx.worldName());
        String profLower = prof.toLowerCase(Locale.ROOT);

        // Extract weather early; anchor it in the system prompt before persona/history
        String weatherLine = extractWeatherFact(worldFacts);
        String weatherSection = weatherLine.isEmpty() ? ""
                : "CURRENT WEATHER:\n" + weatherLine + "\n\n";

        // Load persona file; fall back to persona_default if profession is unknown
        String personaKey = "personas/" + profLower;
        String personaRaw = LOADER.getTemplate(personaKey);
        if (personaRaw.isBlank()) {
            LOGGER.warn("[PromptComposerV1] No persona for '{}', using default", profLower);
            personaRaw = LOADER.getTemplate("persona_default");
        }
        String personaText = extractSection(personaRaw, "### PERSONALITY");

        // Resolve base template variables
        String systemPrompt = LOADER.resolve(LOADER.getTemplate("base"), Map.of(
                "NAME",       name,
                "PROFESSION", prof,
                "WORLD",      world,
                "WEATHER",    weatherSection,
                "PERSONA",    personaText,
                "MOOD",       "neutral"
        ));

        // Deterministic skill routing — no LLM calls
        List<String> skillKeys = SkillRouter.resolveSkills(playerMessage, worldFacts, profLower);
        ExpertiseLevel expertise = ProfessionKnowledge.classify(profLower, playerMessage);

        // Assemble skill sections, capped at MAX_SKILL_CHARS; danger uses persona-specific reaction if available
        StringBuilder skillSb = new StringBuilder();
        for (String key : skillKeys) {
            String skillText;
            if (key.equals("skills/danger")) {
                String dangerReaction = extractSection(personaRaw, "### DANGER REACTION");
                skillText = dangerReaction.isBlank() ? LOADER.getTemplate("skills/danger") : dangerReaction;
            } else {
                skillText = LOADER.getTemplate(key);
            }
            if (skillText.isBlank()) continue;
            if (skillSb.length() + skillText.length() + 2 > MAX_SKILL_CHARS) {
                LOGGER.warn("[PromptComposerV1] skill '{}' dropped — MAX_SKILL_CHARS={} exceeded", key, MAX_SKILL_CHARS);
                break;
            }
            skillSb.append("\n\n").append(skillText);
        }

        messages.add(Map.of("role", "system", "content", systemPrompt + skillSb));

        // 2. Bounded chat history (most recent N)
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

        // 3. Inject world/context facts immediately before the player's question so
        //    live sensor readings are the most proximate context the LLM sees.
        String factsText = formatFacts(worldFacts, playerMessage);
        messages.add(Map.of(
                "role", "system",
                "content", factsText
        ));

        // 4. Language reminder — placed immediately before user message for maximum effect.
        //    Chat history in other languages can overwhelm the system prompt language rule.
        String pm = playerMessage == null ? "" : playerMessage;
        if (!pm.isBlank()) {
            messages.add(Map.of(
                    "role", "system",
                    "content", "REMINDER: The next message is the player's CURRENT message. " +
                            "You MUST reply in the same language as this message, regardless of what language was used earlier in the conversation."
            ));
        }

        // 5. Latest player message
        messages.add(Map.of(
                "role", "user",
                "content", pm
        ));

        return messages;
    }

    // Known multi-word Minecraft phrases. Matched as whole phrases before single-word
    // keyword extraction so compound names like "brewing stand" survive the tokenizer.
    // Add new phrases here as new failure cases are discovered.
    private static final List<String> MINECRAFT_PHRASES = List.of(
            "brewing stand", "blast furnace", "smithing table", "cartography table",
            "fletching table", "grindstone", "loom", "stonecutter", "bell",
            "iron door", "wooden door", "oak door", "spruce door", "trapdoor",
            "iron bar", "glass pane", "stained glass", "diamond ore",
            "deepslate diamond ore", "crying obsidian", "ancient debris",
            "chest", "barrel", "furnace", "crafting table", "enchanting table",
            "ender chest", "shulker box", "composter", "cauldron", "lectern",
            "flower pot", "item frame", "armor stand", "lightning rod",
            "zombie villager", "iron golem", "wandering trader", "cave spider",
            "magma cube", "elder guardian", "wither skeleton", "cow", "pig",
            "bee", "bat", "cat", "dog", "fox", "cod", "yak"
    );

    // Stop words to exclude when extracting query keywords from the player message.
    // "stand" is stopped because it is almost always part of a phrase already consumed
    // by MINECRAFT_PHRASES (e.g. "brewing stand", "armor stand").
    private static final Set<String> QUERY_STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "where", "what", "how", "who", "when", "which", "do", "does",
            "nearest", "closest", "near", "close", "find", "any",
            "i", "me", "my", "you", "your", "it", "to", "of", "in", "on",
            "can", "could", "there", "here", "some", "have", "has", "stand"
    );

    private static String formatFacts(WorldFactBundle bundle, String playerMessage) {
        if (bundle == null || bundle.facts() == null || bundle.facts().isEmpty()) {
            return "WORLD INFO:\n- (none)";
        }

        // Extract query keywords for matching against block facts
        List<String> queryKeywords = extractQueryKeywords(playerMessage);

        String joined = bundle.facts().stream()
                .filter(f -> f != null && f.factText() != null
                        && !f.factText().startsWith("Anchor villager position:")) // strip raw coords
                .map(f -> {
                    String line = "- " + safe(f.factText());
                    if (!queryKeywords.isEmpty()) {
                        String factLower = f.factText().toLowerCase(Locale.ROOT);
                        for (String keyword : queryKeywords) {
                            if (factLower.contains(keyword)) {
                                line += " [MATCHES QUERY]";
                                break;
                            }
                        }
                    }
                    return line;
                })
                .collect(Collectors.joining("\n"));

        // Extract weather fact for explicit constraint (small models need this repeated)
        String weatherConstraint = bundle.facts().stream()
                .filter(f -> f != null && f.factText() != null && f.factText().startsWith("Weather:"))
                .map(f -> "- Current weather: " + safe(f.factText()) +
                          ". When the player asks about weather, you MUST report this. Do NOT say anything different.\n")
                .findFirst()
                .orElse("");

        String text = "WORLD INFO (your senses — complete and accurate):\n" + joined +
                "\n\n" +
                "- This list is exhaustive. If something is not listed above, it is not near you.\n" +
                "- Facts marked [MATCHES QUERY] directly answer the player's question — use them.\n" +
                (weatherConstraint.isEmpty() ? "" : weatherConstraint) +
                "- Describe locations naturally. Never mention 'WORLD INFO', 'FACTS', or 'MATCHES QUERY' in your reply. Only use coordinates if explicitly asked for.\n";

        if (text.length() <= MAX_FACT_CHARS) return text;
        return text.substring(0, MAX_FACT_CHARS - 3) + "...";
    }

    // Extracts meaningful keywords from the player's message for matching against block facts.
    // Two-pass extraction: multi-word Minecraft phrases first, then remaining single words.
    private static List<String> extractQueryKeywords(String playerMessage) {
        if (playerMessage == null || playerMessage.isBlank()) return List.of();

        String normalized = playerMessage.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s]", "");
        List<String> keywords = new ArrayList<>();

        // Pass 1: match known multi-word Minecraft phrases first
        for (String phrase : MINECRAFT_PHRASES) {
            if (normalized.contains(phrase)) {
                keywords.add(phrase); // add full phrase e.g. "brewing stand"
                normalized = normalized.replace(phrase, " "); // consume it so words aren't re-matched
            }
        }

        // Pass 2: match remaining single words
        String[] words = normalized.split("\\s+");
        for (String word : words) {
            if (!word.isBlank() && word.length() > 1 && !QUERY_STOP_WORDS.contains(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    // Extracts the weather fact for early injection into the system prompt.
    // Small models need this anchored at position 0 or they drift mid-generation.
    private static String extractWeatherFact(WorldFactBundle bundle) {
        if (bundle == null || bundle.facts() == null) return "";
        return bundle.facts().stream()
                .filter(f -> f != null && f.factText() != null && f.factText().startsWith("Weather:"))
                .map(f -> "- " + safe(f.factText()) + " — NEVER contradict this in your reply.")
                .findFirst()
                .orElse("");
    }

    /**
     * Extracts the body of a named markdown section (### HEADER) from a persona string.
     * Returns text between this header and the next ### header (or end of string), trimmed.
     * Returns empty string if the header is not found.
     */
    private static String extractSection(String text, String sectionHeader) {
        if (text == null) return "";
        int start = text.indexOf(sectionHeader);
        if (start == -1) return "";
        int contentStart = text.indexOf('\n', start);
        if (contentStart == -1) return "";
        int end = text.indexOf("\n### ", contentStart);
        String raw = (end == -1) ? text.substring(contentStart) : text.substring(contentStart, end);
        return raw.trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("\r", " ").replace("\n", " ").trim();
    }

    private static String safeOrUnknown(String s) {
        String t = s == null ? "" : s.replace("\r", " ").replace("\n", " ").trim();
        return t.isBlank() ? "Unknown" : t;
    }
}
