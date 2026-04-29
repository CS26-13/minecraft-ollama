package net.kevinthedang.ollamamod.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SkillRouter {

    private SkillRouter() {}

    private static final Set<String> HOSTILE_MOBS = Set.of(
        "zombie", "skeleton", "creeper", "spider", "enderman", "witch", "pillager", "ravager",
        "vindicator", "evoker", "vex", "phantom", "drowned", "husk", "stray", "blaze", "ghast",
        "wither", "cave", "slime", "magma", "hoglin", "piglin", "warden", "breeze", "zoglin"
    );

    private static final Set<String> ENV_DANGERS = Set.of("fire", "lava", "tnt");

    /**
     * Determines which skill prompts to inject for this turn. Fully deterministic — no LLM calls.
     * Danger is always first in the returned list when present.
     *
     * @return list of skill keys (e.g. "skills/crafting") to inject into the system prompt
     */
    public static List<String> resolveSkills(String playerMessage, WorldFactBundle worldFacts, String profession) {
        List<String> skills = new ArrayList<>();
        String msgLower = playerMessage == null ? "" : playerMessage.toLowerCase(Locale.ROOT);

        if (isDangerous(worldFacts)) {
            skills.add("skills/danger");
        }
        if (wantsCrafting(msgLower)) {
            skills.add("skills/crafting");
        }
        if (wantsNavigation(msgLower)) {
            skills.add("skills/navigation");
        }
        if (wantsTrading(msgLower)) {
            skills.add("skills/trading");
        }

        return skills;
    }

    // Substring-match each danger keyword against the lowercased fact text.
    // This handles plurals ("fires", "zombies"), punctuation ("fires,"), and
    // phrases ("Three fires, right next to you") without needing tokenization.
    private static boolean isDangerous(WorldFactBundle worldFacts) {
        if (worldFacts == null || worldFacts.facts() == null) return false;
        for (WorldFact fact : worldFacts.facts()) {
            if (fact == null || fact.factText() == null) continue;
            String lower = fact.factText().toLowerCase(Locale.ROOT);
            String matched = null;
            for (String kw : HOSTILE_MOBS) {
                if (lower.contains(kw)) { matched = kw; break; }
            }
            if (matched == null) {
                for (String kw : ENV_DANGERS) {
                    if (lower.contains(kw)) { matched = kw; break; }
                }
            }
            if (matched != null) return true;
        }
        return false;
    }

    private static boolean wantsCrafting(String msgLower) {
        return msgLower.contains("make") || msgLower.contains("craft")
            || msgLower.contains("recipe") || msgLower.contains("how to build")
            || msgLower.contains("how do i get") || msgLower.contains("create")
            || msgLower.contains("smelt");
    }

    private static boolean wantsNavigation(String msgLower) {
        return msgLower.contains("where") || msgLower.contains("find")
            || msgLower.contains("nearest") || msgLower.contains("closest")
            || msgLower.contains("how do i get to") || msgLower.contains("location")
            || msgLower.contains("direction");
    }

    private static boolean wantsTrading(String msgLower) {
        return msgLower.contains("trade") || msgLower.contains("buy")
            || msgLower.contains("sell") || msgLower.contains("cost")
            || msgLower.contains("price") || msgLower.contains("emerald")
            || msgLower.contains("offer") || msgLower.contains("deal");
    }
}
