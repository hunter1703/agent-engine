package com.agentengine.knowledge.api.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.ThreadUtils;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An ordered sequence of {@link ChunkingStage}s.
 *
 * <p>The pipeline streams {@link KnowledgeChunk} instances throughout, rather than materializing a
 * full list between stages: the first stage receives a single-element stream containing a seed
 * chunk with only the document text set, and each later stage transforms that stream further —
 * splitting, merging, or enriching it (e.g. adding vectors) — so a downstream consumer can start
 * acting on early results (e.g. persisting them) before the whole document has finished processing.
 */
public final class ChunkingPipeline {

  private static final Logger LOG = LoggerFactory.getLogger(ChunkingPipeline.class);

  private static final int MIN_CPU_THREADS = 8;

  private static final ExecutorService IO_EXECUTOR =
      ThreadUtils.newVirtualThreadExecutor("chunking-io-vt-");

  private static final ExecutorService CPU_EXECUTOR =
      ThreadUtils.newFixedThreadExecutor(
          "chunking-", Math.max(MIN_CPU_THREADS, Runtime.getRuntime().availableProcessors()));

  private static final Scheduler IO_SCHEDULER = Schedulers.from(IO_EXECUTOR);
  private static final Scheduler CPU_SCHEDULER = Schedulers.from(CPU_EXECUTOR);

  private final List<ChunkingStage> stages;

  private ChunkingPipeline(final List<ChunkingStage> stages) {
    this.stages = stages;
  }

  /**
   * Runs the pipeline starting from a seed chunk containing only the document text. Returns a
   * stream of fully-formed {@link KnowledgeChunk}s ready to persist, emitted as each becomes
   * available rather than only once the whole document has been processed.
   *
   * <p>Every stage runs on a thread this pipeline owns: CPU-bound stages on a dedicated platform
   * pool, all others (which wait on model calls) on virtual threads.
   */
  public Flowable<KnowledgeChunk> run(final KnowledgeChunk seedChunk) {
    Flowable<KnowledgeChunk> current = Flowable.just(seedChunk);
    for (final ChunkingStage stage : stages) {
      final Scheduler scheduler = stage.cpuBound() ? CPU_SCHEDULER : IO_SCHEDULER;
      final AtomicLong startNanos = new AtomicLong();
      final AtomicInteger count = new AtomicInteger();
      current =
          stage
              .apply(current.observeOn(scheduler))
              .filter(chunk -> StringUtils.isNotBlank(chunk.getText()))
              .doOnSubscribe(_ -> startNanos.set(System.nanoTime()))
              .doOnNext(_ -> count.incrementAndGet())
              .doOnComplete(
                  () ->
                      LOG.info(
                          "Chunking stage {} took {}ms, producing {} chunks",
                          stage.getClass().getSimpleName(),
                          (System.nanoTime() - startNanos.get()) / 1_000_000,
                          count.get()));
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
