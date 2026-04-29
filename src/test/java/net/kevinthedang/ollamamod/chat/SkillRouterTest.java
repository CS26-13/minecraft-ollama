package net.kevinthedang.ollamamod.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SkillRouterTest {

    private static WorldFactBundle factsWith(String factText) {
        return new WorldFactBundle(List.of(WorldFact.of(factText, "test", 1.0)));
    }

    @Test
    public void noSkillsForGreeting() {
        List<String> skills = SkillRouter.resolveSkills("hello", WorldFactBundle.empty(), "farmer");
        assertTrue(skills.isEmpty());
    }

    @Test
    public void craftingSkillInjectedForCraftKeyword() {
        List<String> skills = SkillRouter.resolveSkills("how do I craft a sword", WorldFactBundle.empty(), "weaponsmith");
        assertTrue(skills.contains("skills/crafting"));
        assertFalse(skills.contains("skills/danger"));
    }

    @Test
    public void craftingSkillInjectedForMakeKeyword() {
        List<String> skills = SkillRouter.resolveSkills("how do I make a crafting table", WorldFactBundle.empty(), "farmer");
        assertTrue(skills.contains("skills/crafting"));
    }

    @Test
    public void craftingSkillInjectedForSmelt() {
        List<String> skills = SkillRouter.resolveSkills("how do I smelt iron ore", WorldFactBundle.empty(), "toolsmith");
        assertTrue(skills.contains("skills/crafting"));
    }

    @Test
    public void navigationSkillInjectedForWhere() {
        List<String> skills = SkillRouter.resolveSkills("where is the nearest village", WorldFactBundle.empty(), "farmer");
        assertTrue(skills.contains("skills/navigation"));
    }

    @Test
    public void tradingSkillInjectedForEmerald() {
        List<String> skills = SkillRouter.resolveSkills("do you have any emerald deals", WorldFactBundle.empty(), "librarian");
        assertTrue(skills.contains("skills/trading"));
    }

    @Test
    public void dangerSkillInjectedForZombie() {
        List<String> skills = SkillRouter.resolveSkills("hello", factsWith("Nearby entity: zombie"), "farmer");
        assertTrue(skills.contains("skills/danger"));
    }

    @Test
    public void dangerSkillInjectedForLava() {
        List<String> skills = SkillRouter.resolveSkills("hello", factsWith("Lava pool 3 blocks away"), "mason");
        assertTrue(skills.contains("skills/danger"));
    }

    @Test
    public void dangerSkillInjectedForCreeper() {
        List<String> skills = SkillRouter.resolveSkills("hello", factsWith("Nearby entity: creeper, distance 8"), "librarian");
        assertTrue(skills.contains("skills/danger"));
    }

    @Test
    public void dangerSkillIsFirstWhenPresent() {
        List<String> skills = SkillRouter.resolveSkills("how do I craft a sword", factsWith("Nearby entity: zombie"), "weaponsmith");
        assertTrue(skills.contains("skills/danger"));
        assertTrue(skills.contains("skills/crafting"));
        assertEquals("skills/danger", skills.get(0));
    }

    @Test
    public void noSkillsForNullMessage() {
        List<String> skills = SkillRouter.resolveSkills(null, WorldFactBundle.empty(), "farmer");
        assertTrue(skills.isEmpty());
    }

    @Test
    public void noSkillsForNullWorldFacts() {
        List<String> skills = SkillRouter.resolveSkills("hello", null, "farmer");
        assertTrue(skills.isEmpty());
    }

    @Test
    public void multipleSkillsCanBeActive() {
        List<String> skills = SkillRouter.resolveSkills("where can I find diamonds to craft a sword", WorldFactBundle.empty(), "weaponsmith");
        assertTrue(skills.contains("skills/crafting"));
        assertTrue(skills.contains("skills/navigation"));
    }
}
