package net.kevinthedang.ollamamod.vectorstore.model;

public record VectorDocument(
    String id,
    String content,
    float[] embedding,
    VectorMetadata metadata
) {}
