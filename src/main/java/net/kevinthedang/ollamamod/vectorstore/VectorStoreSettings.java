package net.kevinthedang.ollamamod.vectorstore;

public final class VectorStoreSettings {
    public static String ollamaBaseUrl = "http://localhost:11434";

    public static String embeddingModel = "nomic-embed-text";
    public static int embeddingDimension = 768;

    public static int chunkSize = 512;
    public static int chunkOverlap = 64;

    public static int defaultTopK = 5;
    public static double defaultMinScore = 0.5;

    public static String dataDirectory = "ollamamod/vectorstore";
    public static String storeFile = "vectors.store";
    public static String seedStorePath = "/ollamamod/seed/documents.store";

    private VectorStoreSettings() {}
}
