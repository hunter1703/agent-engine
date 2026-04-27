package com.agentengine.knowledge.api.store;

import com.agentengine.knowledge.api.KnowledgeSearchResult;
import java.util.List;
import java.util.Map;

/**
 * Abstraction over a vector database. Implement this interface to switch the underlying
 * vector store without touching any other code in the knowledge module.
 */
public interface VectorStore {

    /** Ensure the collection exists with the given vector dimension. Called at startup. */
    void ensureCollection(int vectorDimension);

    /**
     * Insert or replace a single chunk. The payload is stored alongside the vector and used
     * for metadata filtering and result hydration.
     */
    void upsert(String pointId, float[] vector, Map<String, Object> payload);

    /** Delete all points whose payload matches every entry in the filter map. */
    void deleteByFilter(Map<String, Object> filter);

    /**
     * Nearest-neighbour search. Only points whose payload satisfies the filter are considered.
     * Returns at most {@code maxResults} results with score ≥ {@code minScore}.
     */
    List<KnowledgeSearchResult> search(
            float[] queryVector, Map<String, Object> filter, int maxResults, double minScore);
}
