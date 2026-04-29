package net.kevinthedang.ollamamod.chat;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PromptLoaderTest {

    private final PromptLoader loader = PromptLoader.getInstance();

    @Test
    public void baseTemplateIsLoadedAndContainsNamePlaceholder() {
        String base = loader.getTemplate("base");
        assertFalse(base.isBlank());
        assertTrue(base.contains("{{NAME}}"), "base.md must contain {{NAME}} placeholder");
    }

    @Test
    public void farmerPersonaIsLoaded() {
        String farmer = loader.getTemplate("personas/farmer");
        assertFalse(farmer.isBlank());
        assertTrue(farmer.contains("### PERSONALITY"));
    }

    @Test
    public void allPersonaFilesAreLoaded() {
        String[] professions = {
            "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman",
            "fletcher", "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith"
        };
        for (String p : professions) {
            String text = loader.getTemplate("personas/" + p);
            assertFalse(text.isBlank(), "Persona file for " + p + " should not be blank");
            assertTrue(text.contains("### DANGER REACTION"), p + " persona must have a DANGER REACTION section");
        }
    }

    @Test
    public void missingTemplateReturnsEmptyString() {
        String result = loader.getTemplate("personas/nonexistent_profession");
        assertEquals("", result);
    }

    @Test
    public void resolveReplacesPlaceholder() {
        String result = loader.resolve("Hello {{NAME}}", Map.of("NAME", "Bob"));
        assertEquals("Hello Bob", result);
    }

    @Test
    public void resolveUnknownPlaceholderLeftUnchanged() {
        String result = loader.resolve("Hello {{NAME}}", Map.of());
        assertEquals("Hello {{NAME}}", result);
    }

    @Test
    public void resolveHandlesMultilineValue() {
        String template = "START\n{{PERSONA}}\nEND";
        String value = "Line one\nLine two";
        String result = loader.resolve(template, Map.of("PERSONA", value));
        assertEquals("START\nLine one\nLine two\nEND", result);
    }

    @Test
    public void resolveNullTemplateReturnsEmpty() {
        String result = loader.resolve(null, Map.of("NAME", "Bob"));
        assertEquals("", result);
    }

    @Test
    public void skillFilesAreLoaded() {
        assertFalse(loader.getTemplate("skills/crafting").isBlank());
        assertFalse(loader.getTemplate("skills/danger").isBlank());
        assertFalse(loader.getTemplate("skills/navigation").isBlank());
        assertFalse(loader.getTemplate("skills/trading").isBlank());
    }

    @Test
    public void personaDefaultIsLoaded() {
        String def = loader.getTemplate("persona_default");
        assertFalse(def.isBlank());
        assertTrue(def.contains("### PERSONALITY"));
    }
}
