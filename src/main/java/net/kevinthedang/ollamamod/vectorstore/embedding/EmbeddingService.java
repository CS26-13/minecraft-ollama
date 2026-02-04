package net.kevinthedang.ollamamod.vectorstore.embedding;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EmbeddingService {
    CompletableFuture<float[]> embed(String text);
    CompletableFuture<List<float[]>> embedBatch(List<String> texts);
    int getDimension();
    boolean isHealthy();
}
