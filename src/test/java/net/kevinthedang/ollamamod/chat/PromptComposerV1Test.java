package net.kevinthedang.ollamamod.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PromptComposerV1Test {

    private final PromptComposerV1 composer = new PromptComposerV1();

    private VillagerBrain.Context ctx(String name, String profession) {
        return new VillagerBrain.Context(UUID.randomUUID(), name, profession, "TestWorld");
    }

    private String systemMessage(List<Map<String, String>> messages) {
        return messages.get(0).get("content");
    }

    @Test
    public void systemMessageContainsVillagerName() {
        List<Map<String, String>> messages = composer.buildMessages(
                ctx("Gregorio", "farmer"), List.of(), "hello", WorldFactBundle.empty());
        assertTrue(systemMessage(messages).contains("Gregorio"));
    }

    @Test
    public void systemMessageContainsProfession() {
        List<Map<String, String>> messages = composer.buildMessages(
                ctx("Bob", "weaponsmith"), List.of(), "hello", WorldFactBundle.empty());
        assertTrue(systemMessage(messages).contains("weaponsmith"));
    }

    @Test
    public void farmerPersonaContainsCattywampus() {
        List<Map<String, String>> messages = composer.buildMessages(
                ctx("Huck", "farmer"), List.of(), "hello", WorldFactBundle.empty());
        assertTrue(systemMessage(messages).contains("cattywampus"));
    }

    @Test
    public void craftingSkillInjectedForCraftKeyword() {
        List<Map<String, String>> messages = composer.buildMessages(
                ctx("Bob", "weaponsmith"), List.of(), "how do I craft a sword", WorldFactBundle.empty());
        String system = systemMessage(messages);
        assertTrue(system.contains("crafting grid") || system.contains("CRAFTING KNOWLEDGE"),
                "System prompt should contain crafting instructions");
    }

    @Test
    public void noSkillsForGreeting() {
        List<Map<String, String>> messages = composer.buildMessages(
                ctx("Bob", "farmer"), List.of(), "hello", WorldFactBundle.empty());
        String system = systemMessage(messages);
        assertFalse(system.contains("CRAFTING KNOWLEDGE"));
        assertFalse(system.contains("DANGER AWARENESS"));
    }

    @Test
    public void dangerSkillInjectedForZombieInWorldFacts() {
        WorldFactBundle dangerFacts = new WorldFactBundle(
                List.of(WorldFact.of("Nearby entity: zombie, distance 5", "entity", 1.0)));
        List<Map<String, String>> messages = composer.buildMessages(
                ctx("Alice", "librarian"), List.of(), "hello", dangerFacts);
        String system = systemMessage(messages);
        // Librarian has a custom DANGER REACTION in persona file, so generic danger.md should NOT appear
        assertFalse(system.contains("DANGER AWARENESS"),
                "Librarian should use persona-specific danger reaction, not generic danger.md");
        // But the persona-specific reaction text should be present
        assertTrue(system.contains("u-um") || system.contains("scholar") || system.contains("books"),
                "Librarian-specific danger reaction should appear");
    }

    @Test
    public void weaponsmithDangerReactionShowsExcitement() {
        WorldFactBundle dangerFacts = new WorldFactBundle(
                List.of(WorldFact.of("Nearby entity: zombie", "entity", 1.0)));
        List<Map<String, String>> messages = composer.buildMessages(
                ctx("Grom", "weaponsmith"), List.of(), "hello", dangerFacts);
        String system = systemMessage(messages);
        assertFalse(system.contains("TERRIFIED"),
                "Weaponsmith should not use generic terrified reaction");
    }

    @Test
    public void unknownProfessionFallsBackToDefault() {
        List<Map<String, String>> messages = composer.buildMessages(
                ctx("Unnamed", "nitwit"), List.of(), "hello", WorldFactBundle.empty());
        String system = systemMessage(messages);
        assertTrue(system.contains("friendly") || system.contains("small-town"),
                "Unknown profession should use persona_default.md text");
    }

    @Test
    public void nullContextProducesValidMessages() {
        List<Map<String, String>> messages = composer.buildMessages(
                null, List.of(), "hello", WorldFactBundle.empty());
        assertFalse(messages.isEmpty());
        assertEquals("system", messages.get(0).get("role"));
    }

    @Test
    public void messageStructureIsCorrect() {
        List<Map<String, String>> messages = composer.buildMessages(
                ctx("Alice", "farmer"), List.of(), "hello", WorldFactBundle.empty());
        // system → world facts system → language reminder system → user
        assertEquals("system", messages.get(0).get("role"));
        assertEquals("user", messages.get(messages.size() - 1).get("role"));
        assertEquals("hello", messages.get(messages.size() - 1).get("content"));
    }

    @Test
    public void historyIsIncluded() {
        List<ChatMessage> history = List.of(
                new ChatMessage(ChatRole.PLAYER, "hi"),
                new ChatMessage(ChatRole.VILLAGER, "howdy")
        );
        List<Map<String, String>> messages = composer.buildMessages(
                ctx("Alice", "farmer"), history, "hello", WorldFactBundle.empty());
        // system + 2 history + world facts system + language reminder + user = 6
        assertEquals(6, messages.size());
    }
}
