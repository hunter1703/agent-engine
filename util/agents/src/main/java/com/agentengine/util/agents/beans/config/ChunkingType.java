package com.agentengine.util.agents.beans.config;

import java.util.Locale;

public enum ChunkingType {
  UNKNOWN,
  /** Recursively splits on paragraph, sentence, then character boundaries (default). */
  RECURSIVE,
  /** Splits on sentence boundaries. */
  SENTENCE,
  /** Splits on paragraph boundaries. */
  PARAGRAPH,
  /** Embedding cosine-similarity breakpoint — cuts where topic similarity drops. */
  SEMANTIC,
  /**
   * LLM-guided discourse-aware chunking (LumberChunker style). Expands to: PARAGRAPH → TOKEN_CAP →
   * LLM boundary.
   */
  LLM;

  public static ChunkingType valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return ChunkingType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
