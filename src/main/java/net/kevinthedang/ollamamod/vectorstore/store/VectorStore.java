package net.kevinthedang.ollamamod.vectorstore.store;

import net.kevinthedang.ollamamod.vectorstore.model.MetadataFilter;
import net.kevinthedang.ollamamod.vectorstore.model.VectorDocument;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface VectorStore {
    void store(VectorDocument document);
    void storeAll(List<VectorDocument> documents);

    List<VectorDocument> query(float[] queryEmbedding, MetadataFilter filter,
                               int topK, double minScore);

    Optional<VectorDocument> getById(String documentId);
    boolean delete(String documentId);
    int deleteByFilter(MetadataFilter filter);
    int count(MetadataFilter filter);

    void persist(Path path);
    void load(Path path);
    void loadFromStream(InputStream stream);
    void clear();
}
