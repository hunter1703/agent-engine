package com.agentengine.knowledge.core.pipeline;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.core.chunking.ChunkingPipeline;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.chunking.ChunkingStage;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.models.factories.Model;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Indexes knowledge whose content resolves to one or more {@link ChunkingPipeline} runs, and
 * persists the resulting {@link KnowledgeChunk}s (with vectors already populated by the terminal
 * {@code EmbeddingStage}) to the vector store.
 *
 * <p>Subclasses decide only which files they handle ({@code canIndex}, {@code priority}) and which
 * {@link ChunkingStage}s their file type needs beyond the knowledge's configured chunking strategy
 * (see {@link #stages}); everything else — assembling and running the pipeline, persisting chunks
 * in batches, sampling a content preview, generating a description — is shared here.
 */
public abstract class AbstractTextKnowledgeIndexer implements KnowledgeIndexer {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractTextKnowledgeIndexer.class);
  private static final int PREVIEW_SAMPLE_SIZE = 10;
  private static final int PREVIEW_EXCERPT_LENGTH = 200;
  private static final long DESCRIPTION_TIMEOUT_SECONDS = 8L;
  private static final int INSERT_BATCH_SIZE = 100;

  private final ChunkingPipelineFactory chunkingPipelineFactory;
  private final KnowledgeChunkStore vectorStore;
  protected final DefaultModelsRepository defaultModelsRepository;
  protected final ModelProvider modelProvider;

  protected AbstractTextKnowledgeIndexer(
      final ChunkingPipelineFactory chunkingPipelineFactory,
      final KnowledgeChunkStore vectorStore,
      final DefaultModelsRepository defaultModelsRepository,
      final ModelProvider modelProvider) {
    this.chunkingPipelineFactory = chunkingPipelineFactory;
    this.vectorStore = vectorStore;
    this.defaultModelsRepository = defaultModelsRepository;
    this.modelProvider = modelProvider;
  }

  /**
   * The stages, in order, that turn {@code knowledge}'s raw content into chunks — everything before
   * the terminal embedding stage {@link ChunkingPipelineFactory#create} always appends. Defaults to
   * {@link ChunkingPipelineFactory#getStages}, the stages implementing the knowledge's own
   * configured chunking strategy; a subclass whose file type needs something beyond that — reading
   * a format no strategy stage can parse directly, or describing embedded media — wraps or replaces
   * this default (see {@code PdfKnowledgeIndexer}, {@code OfficeKnowledgeIndexer}, {@code
   * ImageKnowledgeIndexer}).
   */
  protected List<ChunkingStage> stages(final Knowledge knowledge) {
    return chunkingPipelineFactory.getStages(knowledge);
  }

  @Override
  public IndexResult index(final Knowledge knowledge) {
    final ChunkingPipeline pipeline = chunkingPipelineFactory.create(knowledge, stages(knowledge));

    // Chunks are assigned their final id/index/grants, persisted in batches, and sampled for the
    // content preview as they arrive — the pipeline's output is never fully materialized here,
    // even though it can run to thousands of embedded (text + vector) chunks. The final count
    // is a running counter, not a list size; the preview sample is a fixed-size reservoir
    // (Algorithm R), not a list looked up by position, since a stream's length isn't known
    // until it ends.
    final AtomicInteger nextIndex = new AtomicInteger();
    final List<KnowledgeChunk> sample = new ArrayList<>(PREVIEW_SAMPLE_SIZE);
    pipeline
        .run()
        .doOnNext(
            chunk -> {
              // Qdrant requires point IDs to be either unsigned integers or UUIDs
              final int i = nextIndex.getAndIncrement();
              chunk.setId(generateChunkId(knowledge.getId(), i));
              chunk.setChunkIndex(i);
              chunk.setGrants(knowledge.getGrants());
              reservoirSample(sample, chunk, i);
            })
        .buffer(INSERT_BATCH_SIZE)
        .doOnNext(vectorStore::insertMany)
        .ignoreElements()
        .blockingAwait();

    final int totalChunks = nextIndex.get();
    LOG.info("Indexed {} chunks for knowledge {}", totalChunks, knowledge.getId());

    final String preview = contentPreview(sample);
    final String generatedDescription =
        StringUtils.isBlank(knowledge.getDescription())
            ? generateDescription(knowledge.getId(), preview)
            : null;
    return new IndexResult(totalChunks, preview, generatedDescription);
  }

  /**
   * Generates a deterministic UUID for a knowledge chunk. Uses UUID v3 (name-based with MD5) to
   * create a stable, reproducible ID.
   */
  private static String generateChunkId(final String knowledgeId, final int chunkIndex) {
    final String name = knowledgeId + "-" + chunkIndex;
    return UUID.nameUUIDFromBytes(name.getBytes(UTF_8)).toString();
  }

  /**
   * Reservoir sampling (Algorithm R): maintains a uniform random sample of up to {@link
   * #PREVIEW_SAMPLE_SIZE} chunks seen so far, in {@code O(PREVIEW_SAMPLE_SIZE)} space regardless of
   * how many chunks the document produces in total — the total isn't known until the chunk stream
   * ends, so a caller can't sample by position (e.g. "every Nth chunk") without first materializing
   * the whole thing. {@code index} is each chunk's 0-based position in arrival order.
   */
  private static void reservoirSample(
      final List<KnowledgeChunk> reservoir, final KnowledgeChunk chunk, final int index) {
    if (index < PREVIEW_SAMPLE_SIZE) {
      reservoir.add(chunk);
      return;
    }
    final int replaceAt = ThreadLocalRandom.current().nextInt(index + 1);
    if (replaceAt < PREVIEW_SAMPLE_SIZE) {
      reservoir.set(replaceAt, chunk);
    }
  }

  private static String contentPreview(final List<KnowledgeChunk> sampledChunks) {
    final List<String> excerpts = new ArrayList<>(sampledChunks.size());
    for (final KnowledgeChunk chunk : sampledChunks) {
      final String text = chunk.getText() == null ? "" : chunk.getText().strip();
      excerpts.add(
          text.length() <= PREVIEW_EXCERPT_LENGTH
              ? text
              : text.substring(0, PREVIEW_EXCERPT_LENGTH) + "...");
    }
    return String.join("\n[...]\n", excerpts);
  }

  private String generateDescription(final String knowledgeId, final String preview) {
    if (StringUtils.isBlank(preview)) {
      return null;
    }
    final String fastModelId = defaultModelsRepository.getFastModelId();
    if (StringUtils.isBlank(fastModelId)) {
      return null;
    }
    try (RefCounted<Model.LLMModel> refCounted = modelProvider.get(fastModelId)) {
      final String prompt =
          "Excerpts sampled from across a document:\n\n"
              + preview
              + "\n\nIn 1-2 sentences, describe (a) what kind of language or format this is (e.g. "
              + "narrative prose, dialogue/transcript, legal text, tabular data) and (b) what the "
              + "content is about. Output only the description, with no preamble.";
      final LlmRequest request =
          LlmRequest.builder()
              .contents(
                  List.of(
                      Content.builder()
                          .role(Constants.AUTHOR_USER)
                          .parts(Part.fromText(prompt))
                          .build()))
              .build();
      final LlmResponse response =
          refCounted
              .value()
              .model()
              .generateContent(request, false)
              .timeout(DESCRIPTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
              .blockingSingle();
      final String description = response.content().map(Content::text).map(String::trim).orElse("");
      return StringUtils.isNotBlank(description) ? description : null;
    } catch (final Exception e) {
      LOG.debug("Description generation failed for knowledge {}", knowledgeId, e);
      return null;
    }
  }
}
