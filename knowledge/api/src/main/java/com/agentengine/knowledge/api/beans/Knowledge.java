package com.agentengine.knowledge.api;

import com.agentengine.util.common.beans.BaseEntity;

public class Knowledge extends BaseEntity {
    public static final String FIELD_AGENT_ID = "agentId";
    public static final String FIELD_SOURCE_TYPE = "sourceType";
    public static final String FIELD_INDEXING_STATUS = "indexingStatus";

    private String agentId;
    private SourceType sourceType;
    private String source;
    private String title;
    private String description;
    private String embeddingModelId;
    private ChunkingStrategy chunkingStrategy = new ChunkingStrategy();
    private IndexingStatus indexingStatus = IndexingStatus.PENDING;
    private int totalChunks;
    private int totalTokens;
    private long indexedAt;
    private String error;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(final String agentId) {
        this.agentId = agentId;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(final SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(final String source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getEmbeddingModelId() {
        return embeddingModelId;
    }

    public void setEmbeddingModelId(final String embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }

    public ChunkingStrategy getChunkingStrategy() {
        return chunkingStrategy;
    }

    public void setChunkingStrategy(final ChunkingStrategy chunkingStrategy) {
        this.chunkingStrategy = chunkingStrategy == null ? new ChunkingStrategy() : chunkingStrategy;
    }

    public IndexingStatus getIndexingStatus() {
        return indexingStatus;
    }

    public void setIndexingStatus(final IndexingStatus indexingStatus) {
        this.indexingStatus = indexingStatus == null ? IndexingStatus.PENDING : indexingStatus;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(final int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(final int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(final long indexedAt) {
        this.indexedAt = indexedAt;
    }

    public String getError() {
        return error;
    }

    public void setError(final String error) {
        this.error = error;
    }
}
