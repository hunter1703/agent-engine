package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import io.reactivex.rxjava3.core.Flowable;
import java.io.InputStream;

/**
 * Implemented by a {@link ChunkingStage} that can read a document straight from its source {@link
 * InputStream}, without another stage first decoding or parsing it — either because a strategy
 * whose splitting decisions need no semantic understanding of the text (e.g. fixed-size windowing)
 * can read only as much of {@code content} as one window requires at a time, or because the source
 * isn't text at all (e.g. a PDF's binary structure) and only this stage knows how to interpret it.
 *
 * <p>Only usable when the implementing stage is a pipeline's first stage, the only one that ever
 * sees the raw, unsplit document — {@link ChunkingPipeline} calls this instead of {@link
 * ChunkingStage#apply} whenever the first stage implements it.
 */
public interface StreamingChunkingStage {

  Flowable<KnowledgeChunk> apply(InputStream content);
}
