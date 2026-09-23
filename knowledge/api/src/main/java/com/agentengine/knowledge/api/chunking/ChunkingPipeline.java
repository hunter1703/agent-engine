package com.agentengine.knowledge.api.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.ThreadUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * An ordered sequence of {@link ChunkingStage}s.
 *
 * <p>The pipeline operates on {@link KnowledgeChunk} instances throughout. The first stage receives
 * a single-element list containing a seed chunk with only the document text set. Subsequent stages
 * refine, split, or enrich the chunks (e.g. adding vectors).
 */
public final class ChunkingPipeline {

  private static final int MIN_CPU_THREADS = 8;

  private static final ExecutorService IO_EXECUTOR =
      ThreadUtils.newVirtualThreadExecutor("chunking-io-vt-");

  private static final ExecutorService CPU_EXECUTOR =
      ThreadUtils.newFixedThreadExecutor(
          "chunking-", Math.max(MIN_CPU_THREADS, Runtime.getRuntime().availableProcessors()));

  private final List<ChunkingStage> stages;

  private ChunkingPipeline(final List<ChunkingStage> stages) {
    this.stages = stages;
  }

  /**
   * Runs the pipeline starting from a seed chunk containing only the document text. Returns
   * fully-formed {@link KnowledgeChunk}s ready to persist.
   *
   * <p>Every stage runs on a thread this pipeline owns, whatever thread calls it: CPU-bound stages
   * on a dedicated platform pool, all others (which wait on model calls) on virtual threads.
   */
  public List<KnowledgeChunk> run(final KnowledgeChunk seedChunk) {
    List<KnowledgeChunk> current = List.of(seedChunk);
    for (final ChunkingStage stage : stages) {
      current =
          runOn(stage.cpuBound() ? CPU_EXECUTOR : IO_EXECUTOR, stage, current).stream()
              .filter(chunk -> StringUtils.isNotBlank(chunk.getText()))
              .toList();
    }
    return current;
  }

  private static List<KnowledgeChunk> runOn(
      final ExecutorService executor,
      final ChunkingStage stage,
      final List<KnowledgeChunk> chunks) {
    try {
      return executor.submit(() -> stage.apply(chunks)).get();
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while running " + stage.getClass(), ex);
    } catch (final ExecutionException ex) {
      throw ex.getCause() instanceof RuntimeException runtime
          ? runtime
          : new IllegalStateException(ex.getCause());
    }
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
