package com.agentengine.knowledge.api.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import java.util.ArrayList;
import java.util.List;

/**
 * An ordered sequence of {@link ChunkingStage}s.
 *
 * <p>The pipeline operates on {@link KnowledgeChunk} instances throughout. The first stage
 * receives a single-element list containing a seed chunk with only the document text set.
 * Subsequent stages refine, split, or enrich the chunks (e.g. adding vectors).
 */
public final class ChunkingPipeline {

    private final List<ChunkingStage> stages;

    private ChunkingPipeline(final List<ChunkingStage> stages) {
        this.stages = stages;
    }

    /**
     * Runs the pipeline starting from a seed chunk containing only the document text.
     * Returns fully-formed {@link KnowledgeChunk}s ready to persist.
     */
    public List<KnowledgeChunk> run(final KnowledgeChunk seedChunk) {
        List<KnowledgeChunk> current = List.of(seedChunk);
        for (final ChunkingStage stage : stages) {
            current = stage.apply(current);
        }
        return current;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<ChunkingStage> stages = new ArrayList<>();

        public Builder then(final ChunkingStage stage) {
            stages.add(stage);
            return this;
        }

        public ChunkingPipeline build() {
            if (stages.isEmpty()) {
                throw new IllegalStateException("Pipeline must have at least one stage");
            }
            return new ChunkingPipeline(List.copyOf(stages));
        }
    }
}
