package net.kevinthedang.ollamamod.vectorstore.chunker;

import net.kevinthedang.ollamamod.vectorstore.VectorStoreSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConversationChunkerTest {
    private final int originalChunkSize = VectorStoreSettings.chunkSize;

    // Restore static settings after each test.
    @AfterEach
    public void tearDown() {
        VectorStoreSettings.chunkSize = originalChunkSize;
    }

    // Conversations should keep Player/Villager exchanges together.
    @Test
    public void keepsPlayerVillagerExchangesTogether() {
        VectorStoreSettings.chunkSize = 40;
        ConversationChunker chunker = new ConversationChunker();
        String content = "Player: Hello\nVillager: Hi there\nPlayer: Trade?\nVillager: Sure";
        List<String> chunks = chunker.chunk(content);
        for (String chunk : chunks) {
            assertTrue(chunk.contains("Player:"), "Chunk should contain Player line");
            assertTrue(chunk.contains("Villager:"), "Chunk should contain Villager line");
        }
    }
}
