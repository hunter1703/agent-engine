package com.agentengine.knowledge.api.beans;

//TODO: no total chunks?
public class KnowledgeSearchResult {
    private String knowledgeId;
    private String agentId;
    private int chunkIndex;
    private String text;
    private double score;
    private String source;
    private String sourceType;
    private int chunkStart;
    private int chunkEnd;

    public KnowledgeSearchResult() {}

    public KnowledgeSearchResult(
            final String knowledgeId,
            final String agentId,
            final int chunkIndex,
            final String text,
            final double score,
            final String source,
            final String sourceType,
            final int chunkStart,
            final int chunkEnd) {
        this.knowledgeId = knowledgeId;
        this.agentId = agentId;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.score = score;
        this.source = source;
        this.sourceType = sourceType;
        this.chunkStart = chunkStart;
        this.chunkEnd = chunkEnd;
    }

    public String getKnowledgeId() {
        return knowledgeId;
    }

    public void setKnowledgeId(final String knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(final String agentId) {
        this.agentId = agentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(final int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getText() {
        return text;
    }

    public void setText(final String text) {
        this.text = text;
    }

    public double getScore() {
        return score;
    }

    public void setScore(final double score) {
        this.score = score;
    }

    public String getSource() {
        return source;
    }

    public void setSource(final String source) {
        this.source = source;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(final String sourceType) {
        this.sourceType = sourceType;
    }

    public int getChunkStart() {
        return chunkStart;
    }

    public void setChunkStart(final int chunkStart) {
        this.chunkStart = chunkStart;
    }

    public int getChunkEnd() {
        return chunkEnd;
    }

    public void setChunkEnd(final int chunkEnd) {
        this.chunkEnd = chunkEnd;
    }
}
