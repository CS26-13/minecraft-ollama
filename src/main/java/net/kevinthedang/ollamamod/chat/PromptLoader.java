package net.kevinthedang.ollamamod.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PromptLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(PromptLoader.class);
    private static final PromptLoader INSTANCE = new PromptLoader();

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private PromptLoader() {
        preload("base",              "prompts/base.md");
        preload("persona_default",   "prompts/persona_default.md");
        preload("skills/crafting",   "prompts/skills/crafting.md");
        preload("skills/danger",     "prompts/skills/danger.md");
        preload("skills/navigation", "prompts/skills/navigation.md");
        preload("skills/trading",    "prompts/skills/trading.md");
        String[] professions = {
            "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman",
            "fletcher", "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith"
        };
        for (String p : professions) {
            preload("personas/" + p, "prompts/personas/" + p + ".md");
        }
    }

    public static PromptLoader getInstance() {
        return INSTANCE;
    }

    /** Returns the cached template text for the given key, or empty string if not found. */
    public String getTemplate(String key) {
        return cache.getOrDefault(key, "");
    }

    /**
     * Replaces {{VAR}} placeholders in template with values from vars map.
     * Uses literal String.replace — safe for multi-line replacement values.
     * Unknown placeholders are left unchanged.
     */
    public String resolve(String template, Map<String, String> vars) {
        if (template == null || template.isBlank()) return "";
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private void preload(String key, String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOGGER.warn("[PromptLoader] Resource not found: {}", resourcePath);
                cache.put(key, "");
                return;
            }
            cache.put(key, new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOGGER.warn("[PromptLoader] Failed to load {}: {}", resourcePath, e.getMessage());
            cache.put(key, "");
        }
    }
}
