package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.CollectionUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable knowledge configuration attached to any entity that participates in knowledge
 * indexing or retrieval — currently {@code BaseAgentConfig} and individual knowledge bases.
 *
 * <h2>Pipeline configuration</h2>
 * <p>Use {@link #chunkingStrategy} to define an explicit ordered sequence of chunking stages, each with
 * its own parameters.
 *
 * <p>Example — LLM coarse sections refined by cosine sub-chunking:
 * <pre>{@code
 * "pipeline": [
 *   {"type": "PARAGRAPH"},
 *   {"type": "TOKEN_CAP", "maxTokensPerSegment": 1024},
 *   {"type": "LLM"},
 *   {"type": "SENTENCE"},
 *   {"type": "SEMANTIC", "similarityThreshold": 0.5},
 *   {"type": "SIZE_MERGE"}
 * ]
 * }</pre>
 */
public class KnowledgeSettings {

    /**
     * ID of the embedding model to use for indexing and search.
     * Falls back to {@code DefaultModelsConfig.embeddingModelId} when null.
     */
    private String embeddingModelId;

    private String chatModelId;

    /**
     * Explicit ordered pipeline of chunking stages. Each entry is a fully configured {@link ChunkingStrategy}
     * describing one stage and its parameters.
     */
    private List<ChunkingStrategy> chunkingStrategy = new ArrayList<>();

    public String getEmbeddingModelId() {
        return embeddingModelId;
    }

    public void setEmbeddingModelId(final String embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }

    public String getChatModelId() {
        return chatModelId;
    }

    public void setChatModelId(final String chatModelId) {
        this.chatModelId = chatModelId;
    }

    public List<ChunkingStrategy> getChunkingStrategy() {
        return chunkingStrategy;
    }

    public void setChunkingStrategy(final List<ChunkingStrategy> chunkingStrategy) {
        this.chunkingStrategy = CollectionUtils.nullSafeList(chunkingStrategy);
    }
}
