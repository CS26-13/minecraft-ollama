package net.kevinthedang.ollamamod.vectorstore.chunker;

import net.kevinthedang.ollamamod.vectorstore.VectorStoreSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonChunkerTest {
    private final int originalChunkSize = VectorStoreSettings.chunkSize;

    // Restore static settings after each test.
    @AfterEach
    public void tearDown() {
        VectorStoreSettings.chunkSize = originalChunkSize;
    }

    // Invalid JSON should throw an error.
    @Test
    public void invalidJsonThrows() {
        VectorStoreSettings.chunkSize = 50;
        JsonChunker chunker = new JsonChunker();
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk("{ invalid"));
    }

    // Nested arrays should be chunked by their sub-elements.
    @Test
    public void nestedArraysPrioritizeSubEntries() {
        VectorStoreSettings.chunkSize = 60;
        JsonChunker chunker = new JsonChunker();
        String content = "{\"recipes\":[{\"name\":\"a\",\"inputs\":[1,2]}," +
            "{\"name\":\"b\",\"inputs\":[3,4]}],\"meta\":{\"version\":1}}";
        List<String> chunks = chunker.chunk(content);
        assertTrue(!chunks.isEmpty(), "Expected chunks derived from nested array entries");
        boolean foundRecipeEntry = false;
        for (String chunk : chunks) {
            assertTrue(!chunk.contains("\"meta\""), "Chunk should not include top-level meta object");
            assertTrue(chunk.length() <= VectorStoreSettings.chunkSize,
                "Chunk should respect max chunk size");
            if (chunk.contains("name")) {
                foundRecipeEntry = true;
            }
        }
        assertTrue(foundRecipeEntry, "Expected at least one recipe entry chunk");
    }

    // Small JSON objects should stay whole when under the chunk size.
    @Test
    public void smallJsonStaysWhole() {
        VectorStoreSettings.chunkSize = 200;
        JsonChunker chunker = new JsonChunker();
        String content = "{\"name\":\"small\",\"inputs\":[1,2],\"meta\":{\"version\":1}}";
        List<String> chunks = chunker.chunk(content);
        assertTrue(chunks.size() == 1, "Expected a single chunk for small JSON");
        assertTrue(chunks.get(0).contains("\"name\""), "Chunk should include the object");
    }

    // Large JSON objects should drill into nested object arrays rather than primitives.
    @Test
    public void largeJsonDrillsIntoObjectArrays() {
        VectorStoreSettings.chunkSize = 60;
        JsonChunker chunker = new JsonChunker();
        String content = "{\"recipes\":[{\"name\":\"a\",\"inputs\":[1,2,3,4,5]},"
            + "{\"name\":\"b\",\"inputs\":[6,7,8,9,10]}],\"meta\":{\"version\":1}}";
        List<String> chunks = chunker.chunk(content);
        boolean foundRecipeEntry = false;
        for (String chunk : chunks) {
            if (chunk.contains("\"name\"")) {
                foundRecipeEntry = true;
            }
            assertTrue(!chunk.contains("\"meta\""), "Chunk should not include top-level meta object");
        }
        assertTrue(foundRecipeEntry, "Expected recipe entries to be chunked");
    }
}
