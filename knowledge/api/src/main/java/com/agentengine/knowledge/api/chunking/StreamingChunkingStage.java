package com.agentengine.knowledge.api.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import io.reactivex.rxjava3.core.Flowable;
import java.io.Reader;

/**
 * Implemented by a {@link ChunkingStage} that can split a document straight from its source {@link
 * Reader}, without needing the whole document materialized as a string first — a strategy whose
 * splitting decisions need no semantic understanding of the text (e.g. fixed-size windowing) can
 * read only as much of {@code content} as one window requires at a time.
 *
 * <p>Only usable when the implementing stage is a pipeline's first stage, the only one that ever
 * sees the raw, unsplit document — {@link ChunkingPipeline} calls this instead of {@link
 * ChunkingStage#apply} whenever the first stage implements it.
 */
public interface StreamingChunkingStage {

  Flowable<KnowledgeChunk> apply(Reader content);
}
