package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.utils.ThreadUtils;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** An ordered sequence of {@link IndexingStage}s. */
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

  private final RootIndexingStage rootStage;
  private final List<IndexingStage> stages;

  public ChunkingPipeline(RootIndexingStage rootStage, final List<IndexingStage> stages) {
    this.rootStage = rootStage;
    this.stages = stages;
  }

  public Flowable<KnowledgeChunk> run() {
    final Scheduler rootScheduler = rootStage.cpuBound() ? CPU_SCHEDULER : IO_SCHEDULER;
    Flowable<KnowledgeChunk> current =
        withInstrumentation(rootStage, rootStage.apply().subscribeOn(rootScheduler));
    for (final IndexingStage stage : stages) {
      final Scheduler scheduler = stage.cpuBound() ? CPU_SCHEDULER : IO_SCHEDULER;
      current =
          withInstrumentation(
              stage, stage.apply(current.observeOn(scheduler)).subscribeOn(scheduler));
    }
    return current;
  }

  private Flowable<KnowledgeChunk> withInstrumentation(
      final IndexingStage stage, final Flowable<KnowledgeChunk> stageOutput) {
    final AtomicLong startNanos = new AtomicLong();
    final AtomicInteger count = new AtomicInteger();
    return stageOutput
        .filter(chunk -> ChunkUtils.isMedia(chunk) || StringUtils.isNotBlank(chunk.getText()))
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
}
