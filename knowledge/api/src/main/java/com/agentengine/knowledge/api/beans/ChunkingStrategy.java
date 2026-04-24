package com.agentengine.knowledge.api.beans;

public class ChunkingStrategy {
    private String type = ChunkingType.RECURSIVE.name();
    private int maxSegmentSize = 512;
    private int maxOverlapSize = 50;

    public ChunkingStrategy() {}

    public ChunkingStrategy(final ChunkingType type, final int maxSegmentSize, final int maxOverlapSize) {
        this.type = type.name();
        this.maxSegmentSize = maxSegmentSize;
        this.maxOverlapSize = maxOverlapSize;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type == null ? ChunkingType.RECURSIVE.name() : type;
    }

    public int getMaxSegmentSize() {
        return maxSegmentSize;
    }

    public void setMaxSegmentSize(final int maxSegmentSize) {
        this.maxSegmentSize = maxSegmentSize;
    }

    public int getMaxOverlapSize() {
        return maxOverlapSize;
    }

    public void setMaxOverlapSize(final int maxOverlapSize) {
        this.maxOverlapSize = maxOverlapSize;
    }
}
