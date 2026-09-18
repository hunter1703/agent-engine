package com.agentengine.knowledge.api.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import java.util.List;

/**
 * A single step in a {@link ChunkingPipeline}.
 *
 * <p>Each stage receives a list of {@link KnowledgeChunk}s from the previous stage and returns a
 * refined list. The first stage always receives a single-element list containing a chunk with only
 * the full document text set (no vector, no offsets yet).
 *
 * <p>Splitting stages produce new chunks from the text of each input chunk. The embedding stage
 * populates the vector on each chunk.
 *
 * <p>Implementations must be stateless — the same instance may be reused across concurrent indexing
 * operations.
 */
public abstract class ChunkingStage {

  protected abstract List<KnowledgeChunk> apply(List<KnowledgeChunk> chunks);

  /**
   * Whether this stage is pure computation with no blocking I/O. The pipeline runs such stages on a
   * dedicated platform thread pool, since a virtual thread never yields its carrier while
   * computing.
   */
  protected boolean cpuBound() {
    return false;
  }
}
