package net.kevinthedang.ollamamod.chat;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ProfessionKnowledge {

    private ProfessionKnowledge() {}

    static final Map<String, Set<String>> EXPERTISE = Map.ofEntries(
        Map.entry("farmer",        Set.of("crop", "grow", "bone meal", "compost", "breed",
                                          "hoe", "wheat", "carrot", "potato", "beetroot",
                                          "melon", "pumpkin", "seed", "plant", "harvest")),
        Map.entry("librarian",     Set.of("enchant", "enchantment", "book", "lectern", "xp",
                                          "mending", "unbreaking", "sharpness", "protection",
                                          "fortune", "enchanting table")),
        Map.entry("cleric",        Set.of("potion", "brew", "brewing stand", "nether wart",
                                          "blaze", "splash", "lingering", "heal", "regeneration")),
        Map.entry("weaponsmith",   Set.of("sword", "axe", "damage", "weapon", "sharpness",
                                          "smite", "sweeping", "knockback", "anvil repair")),
        Map.entry("armorer",       Set.of("armor", "defense", "netherite", "shield", "chainmail",
                                          "diamond armor", "iron armor")),
        Map.entry("toolsmith",     Set.of("tool", "pickaxe", "durability", "efficiency",
                                          "silk touch", "fortune", "tier", "ore")),
        Map.entry("cartographer",  Set.of("biome", "structure", "map", "explorer", "compass",
                                          "lodestone")),
        Map.entry("fisherman",     Set.of("fish", "fishing", "rain", "lure", "luck of the sea",
                                          "fishing rod", "treasure")),
        Map.entry("fletcher",      Set.of("arrow", "crossbow", "bow", "tipped", "infinity",
                                          "spectral", "power")),
        Map.entry("mason",         Set.of("stone", "smelt", "decorative", "stonecutter",
                                          "polished", "andesite", "diorite", "granite")),
        Map.entry("shepherd",      Set.of("wool", "dye", "sheep", "bed", "banner", "color")),
        Map.entry("butcher",       Set.of("food", "hunger", "saturation", "cook", "smoker",
                                          "steak", "porkchop", "chicken")),
        Map.entry("leatherworker", Set.of("leather", "rabbit hide", "horse armor", "item frame",
                                          "dye"))
    );

    /**
     * Classifies how well the current profession matches the player's message.
     * Uses substring matching to handle multi-word keywords like "bone meal" or "silk touch".
     */
    public static ExpertiseLevel classify(String profession, String playerMessage) {
        if (profession == null || playerMessage == null) return ExpertiseLevel.FAMILIAR;
        String prof = profession.trim().toLowerCase(Locale.ROOT);
        String msg  = playerMessage.toLowerCase(Locale.ROOT);

        Set<String> ownKeywords = EXPERTISE.getOrDefault(prof, Set.of());
        for (String kw : ownKeywords) {
            if (msg.contains(kw)) return ExpertiseLevel.EXPERT;
        }

        for (Map.Entry<String, Set<String>> entry : EXPERTISE.entrySet()) {
            if (entry.getKey().equals(prof)) continue;
            for (String kw : entry.getValue()) {
                if (msg.contains(kw)) return ExpertiseLevel.UNKNOWN;
            }
        }

        return ExpertiseLevel.FAMILIAR;
    }

    /**
     * When classify() returns UNKNOWN, returns the profession name that best matches
     * the player message by keyword hit count. Returns empty string if no match.
     */
    public static String suggestRedirect(String playerMessage) {
        if (playerMessage == null) return "";
        String msg = playerMessage.toLowerCase(Locale.ROOT);
        String bestProfession = "";
        int bestCount = 0;

        for (Map.Entry<String, Set<String>> entry : EXPERTISE.entrySet()) {
            int count = 0;
            for (String kw : entry.getValue()) {
                if (msg.contains(kw)) count++;
            }
            if (count > bestCount) {
                bestCount = count;
                bestProfession = entry.getKey();
            }
        }
        return bestProfession;
    }
}
