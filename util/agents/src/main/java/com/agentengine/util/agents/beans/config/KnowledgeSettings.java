package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.utils.CollectionUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reusable knowledge configuration attached to any entity that participates in knowledge indexing
 * or retrieval — currently {@code BaseAgentConfig} and individual knowledge bases.
 *
 * <h2>Pipeline configuration</h2>
 *
 * <p>Use {@link #chunkingStrategy} to define an explicit ordered sequence of chunking stages, each
 * with its own parameters.
 *
 * <p>Example — LLM coarse sections refined by cosine sub-chunking:
 *
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
   * Explicit ordered pipeline of chunking stages. Each entry is a fully configured {@link
   * ChunkingStrategy} describing one stage and its parameters.
   */
  private List<ChunkingStrategy> chunkingStrategy = new ArrayList<>();

  /**
   * The embedding models, by id, that embed each modality, keyed by modality name ({@code TEXT},
   * {@code IMAGE}), in addition to the customer's default embedding model of that modality, which
   * always embeds.
   */
  private Map<String, List<String>> modalityVsEmbeddingModelIds = new LinkedHashMap<>();

  /** Whether media is described to text while indexing. */
  private boolean describeMedia = true;

  public List<ChunkingStrategy> getChunkingStrategy() {
    return chunkingStrategy;
  }

  public void setChunkingStrategy(final List<ChunkingStrategy> chunkingStrategy) {
    this.chunkingStrategy = CollectionUtils.nullSafeList(chunkingStrategy);
  }

  public Map<String, List<String>> getModalityVsEmbeddingModelIds() {
    return modalityVsEmbeddingModelIds;
  }

  public void setModalityVsEmbeddingModelIds(
      final Map<String, List<String>> modalityVsEmbeddingModelIds) {
    this.modalityVsEmbeddingModelIds = CollectionUtils.nullSafeMap(modalityVsEmbeddingModelIds);
  }

  public boolean isDescribeMedia() {
    return describeMedia;
  }

  public void setDescribeMedia(final boolean describeMedia) {
    this.describeMedia = describeMedia;
  }
}
