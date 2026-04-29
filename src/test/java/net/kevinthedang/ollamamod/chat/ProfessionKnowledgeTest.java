package net.kevinthedang.ollamamod.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProfessionKnowledgeTest {

    @Test
    public void farmerExpertOnWheat() {
        assertEquals(ExpertiseLevel.EXPERT, ProfessionKnowledge.classify("farmer", "how do I grow wheat"));
    }

    @Test
    public void farmerUnknownOnEnchanting() {
        assertEquals(ExpertiseLevel.UNKNOWN, ProfessionKnowledge.classify("farmer", "how do I enchant my sword"));
    }

    @Test
    public void farmerFamiliarOnGreeting() {
        assertEquals(ExpertiseLevel.FAMILIAR, ProfessionKnowledge.classify("farmer", "hello"));
    }

    @Test
    public void weaponsmithExpertOnSword() {
        assertEquals(ExpertiseLevel.EXPERT, ProfessionKnowledge.classify("weaponsmith", "how do I craft a sword"));
    }

    @Test
    public void librarianUnknownOnSword() {
        assertEquals(ExpertiseLevel.UNKNOWN, ProfessionKnowledge.classify("librarian", "how do I craft a sword"));
    }

    @Test
    public void clericExpertOnPotion() {
        assertEquals(ExpertiseLevel.EXPERT, ProfessionKnowledge.classify("cleric", "how do I brew a potion"));
    }

    @Test
    public void suggestRedirectForEnchanting() {
        // "enchantment", "enchanting table", "mending", "xp" — 4+ librarian hits, 0 weaponsmith hits
        assertEquals("librarian", ProfessionKnowledge.suggestRedirect("how do I use the enchanting table to get mending at the xp level I need"));
    }

    @Test
    public void suggestRedirectReturnsEmptyForGenericMessage() {
        assertEquals("", ProfessionKnowledge.suggestRedirect("hello there"));
    }

    @Test
    public void nullProfessionReturnsFamiliar() {
        assertEquals(ExpertiseLevel.FAMILIAR, ProfessionKnowledge.classify(null, "hello"));
    }

    @Test
    public void nullMessageReturnsFamiliar() {
        assertEquals(ExpertiseLevel.FAMILIAR, ProfessionKnowledge.classify("farmer", null));
    }

    @Test
    public void armorerExpertOnArmor() {
        assertEquals(ExpertiseLevel.EXPERT, ProfessionKnowledge.classify("armorer", "what is the best armor in the game"));
    }

    @Test
    public void shepherdExpertOnWool() {
        assertEquals(ExpertiseLevel.EXPERT, ProfessionKnowledge.classify("shepherd", "how do I dye wool red"));
    }

    @Test
    public void caseInsensitiveProfession() {
        assertEquals(ExpertiseLevel.EXPERT, ProfessionKnowledge.classify("FARMER", "how do I grow wheat"));
    }
}
