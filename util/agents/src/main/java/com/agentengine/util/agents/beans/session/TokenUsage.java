package com.agentengine.util.agents.beans.session;

/**
 * Tokens a session's model calls used, summed over its committed turns. Output tokens exclude
 * thinking tokens, which are counted separately; cached tokens are the part of the input the
 * provider served from its cache.
 */
public class TokenUsage {
  public static final String FIELD_INPUT_TOKENS = "inputTokens";
  public static final String FIELD_OUTPUT_TOKENS = "outputTokens";
  public static final String FIELD_THINKING_TOKENS = "thinkingTokens";
  public static final String FIELD_CACHED_TOKENS = "cachedTokens";
  public static final String FIELD_TOTAL_TOKENS = "totalTokens";

  private long inputTokens;
  private long outputTokens;
  private long thinkingTokens;
  private long cachedTokens;
  private long totalTokens;

  public TokenUsage() {}

  public long getInputTokens() {
    return inputTokens;
  }

  public void setInputTokens(final long inputTokens) {
    this.inputTokens = inputTokens;
  }

  public long getOutputTokens() {
    return outputTokens;
  }

  public void setOutputTokens(final long outputTokens) {
    this.outputTokens = outputTokens;
  }

  public long getThinkingTokens() {
    return thinkingTokens;
  }

  public void setThinkingTokens(final long thinkingTokens) {
    this.thinkingTokens = thinkingTokens;
  }

  public long getCachedTokens() {
    return cachedTokens;
  }

  public void setCachedTokens(final long cachedTokens) {
    this.cachedTokens = cachedTokens;
  }

  public long getTotalTokens() {
    return totalTokens;
  }

  public void setTotalTokens(final long totalTokens) {
    this.totalTokens = totalTokens;
  }
}
