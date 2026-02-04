package net.kevinthedang.ollamamod.vectorstore.chunker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonChunkerTest {
    // Invalid JSON should throw an error.
    @Test
    public void invalidJsonThrows() {
        JsonChunker chunker = new JsonChunker(50);
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk("{ invalid"));
    }

    // Nested arrays should be chunked by their sub-elements.
    @Test
    public void nestedArraysPrioritizeSubEntries() {
        int chunkSize = 60;
        JsonChunker chunker = new JsonChunker(chunkSize);
        String content = "{\"recipes\":[{\"name\":\"a\",\"inputs\":[1,2]}," +
            "{\"name\":\"b\",\"inputs\":[3,4]}],\"meta\":{\"version\":1}}";
        List<String> chunks = chunker.chunk(content);
        assertTrue(!chunks.isEmpty(), "Expected chunks derived from nested array entries");
        boolean foundRecipeEntry = false;
        for (String chunk : chunks) {
            assertTrue(!chunk.contains("\"meta\""), "Chunk should not include top-level meta object");
            assertTrue(chunk.length() <= chunkSize,
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
        JsonChunker chunker = new JsonChunker(200);
        String content = "{\"name\":\"small\",\"inputs\":[1,2],\"meta\":{\"version\":1}}";
        List<String> chunks = chunker.chunk(content);
        assertTrue(chunks.size() == 1, "Expected a single chunk for small JSON");
        assertTrue(chunks.get(0).contains("\"name\""), "Chunk should include the object");
    }

    // Large JSON objects should drill into nested object arrays rather than primitives.
    @Test
    public void largeJsonDrillsIntoObjectArrays() {
        JsonChunker chunker = new JsonChunker(60);
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
