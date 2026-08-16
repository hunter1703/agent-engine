package com.agentengine.util.agents.beans.config;

public class ChunkingStrategy {

  /** The chunking technique this stage applies. Defaults to {@link ChunkingType#RECURSIVE}. */
  private String type = ChunkingType.PARAGRAPH.name();

  private int maxSegmentSize = 512;
  private int maxOverlapSize = 50;

  /**
   * Cosine similarity threshold for {@code SEMANTIC} chunking. Defaults to the 25th-percentile
   * heuristic when set to 0.
   */
  private double similarityThreshold = 0.0;

  /**
   * Maximum estimated tokens per segment fed into the LLM boundary stage. Approximated as {@code
   * chars / 3}. Defaults to 1024.
   */
  private int maxTokensPerSegment = 1024;

  private int approxCharsPerToken = 3;

  public ChunkingStrategy() {}

  public ChunkingStrategy(
      final ChunkingType type, final int maxSegmentSize, final int maxOverlapSize) {
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

  public double getSimilarityThreshold() {
    return similarityThreshold;
  }

  public void setSimilarityThreshold(final double similarityThreshold) {
    this.similarityThreshold = similarityThreshold;
  }

  public int getMaxTokensPerSegment() {
    return maxTokensPerSegment;
  }

  public void setMaxTokensPerSegment(final int maxTokensPerSegment) {
    this.maxTokensPerSegment = maxTokensPerSegment;
  }

  public int getApproxCharsPerToken() {
    return approxCharsPerToken;
  }

  public void setApproxCharsPerToken(final int approxCharsPerToken) {
    this.approxCharsPerToken = approxCharsPerToken;
  }
}
