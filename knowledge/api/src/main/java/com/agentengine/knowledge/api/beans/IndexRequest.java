package com.agentengine.knowledge.api.beans;

public class IndexRequest {
    private String agentId;
    private String sourceType;
    private String source;
    private String title;
    private String description;
    private String embeddingModelId;
    private ChunkingStrategy chunkingStrategy;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(final String agentId) {
        this.agentId = agentId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(final String sourceType) {
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
        this.chunkingStrategy = chunkingStrategy;
    }
}
