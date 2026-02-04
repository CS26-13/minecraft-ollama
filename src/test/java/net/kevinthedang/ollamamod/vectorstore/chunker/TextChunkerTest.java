package net.kevinthedang.ollamamod.vectorstore.chunker;

import net.kevinthedang.ollamamod.vectorstore.VectorStoreSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TextChunkerTest {
    private final int originalChunkSize = VectorStoreSettings.chunkSize;
    private final int originalOverlap = VectorStoreSettings.chunkOverlap;

    // Restore static settings after each test.
    @AfterEach
    public void tearDown() {
        VectorStoreSettings.chunkSize = originalChunkSize;
        VectorStoreSettings.chunkOverlap = originalOverlap;
    }

    // Short text should return a single chunk.
    @Test
    public void shortTextReturnsSingleChunk() {
        VectorStoreSettings.chunkSize = 100;
        VectorStoreSettings.chunkOverlap = 10;
        TextChunker chunker = new TextChunker();
        List<String> chunks = chunker.chunk("Short text.");
        assertTrue(chunks.size() == 1, "Expected a single chunk");
        assertFalse(chunks.get(0).isEmpty(), "Chunk should not be empty");
    }

    // Long text should split into multiple sentence-aligned chunks.
    @Test
    public void longTextSplitsIntoMultipleChunks() {
        VectorStoreSettings.chunkSize = 40;
        VectorStoreSettings.chunkOverlap = 5;
        TextChunker chunker = new TextChunker();
        String content = "Sentence one. Sentence two. Sentence three. Sentence four.";
        List<String> chunks = chunker.chunk(content);
        assertTrue(chunks.size() > 1, "Expected multiple chunks");
        for (String chunk : chunks) {
            assertFalse(chunk.isEmpty(), "Chunk should not be empty");
        }
    }
}
