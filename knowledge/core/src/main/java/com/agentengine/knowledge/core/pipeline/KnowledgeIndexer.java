package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;

public interface KnowledgeIndexer {

  boolean canIndex(Knowledge knowledge);

  IndexResult index(Knowledge knowledge);

  int priority();

  /**
   * @param generatedDescription an auto-generated description of the content's register and topic,
   *     derived from a sample of the indexed chunks — {@code null} when the knowledge already had a
   *     user-supplied description, or when generating one wasn't possible.
   */
  record IndexResult(int chunkCount, String contentPreview, String generatedDescription) {}
}
