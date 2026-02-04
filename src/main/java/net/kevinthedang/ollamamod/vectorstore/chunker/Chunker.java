package net.kevinthedang.ollamamod.vectorstore.chunker;

import net.kevinthedang.ollamamod.vectorstore.model.VectorDocument;
import net.kevinthedang.ollamamod.vectorstore.model.VectorMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class Chunker {
    protected final int maxChunkSize;
    protected final int overlapSize;

    // Base chunker with size and overlap configuration.
    public Chunker(int maxChunkSize, int overlapSize) {
        this.maxChunkSize = maxChunkSize;
        this.overlapSize = overlapSize;
    }

    // Split raw content into chunk strings.
    public abstract List<String> chunk(String content);

    // Convert chunk strings into VectorDocuments with chunk metadata.
    public List<VectorDocument> chunkToDocuments(String content, VectorMetadata baseMetadata) {
        List<String> chunks = chunk(content);
        List<VectorDocument> documents = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            VectorMetadata chunkMetadata = baseMetadata.withChunk(index, chunks.size());
            documents.add(new VectorDocument(
                UUID.randomUUID().toString(),
                chunks.get(index),
                null,
                chunkMetadata
            ));
        }
        return documents;
    }
}
