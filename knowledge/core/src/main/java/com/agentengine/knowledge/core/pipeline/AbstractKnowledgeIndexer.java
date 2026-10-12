package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.IndexingStatus;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.core.chunking.ChunkingPipeline;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.chunking.IndexingStage;
import com.agentengine.knowledge.core.chunking.RootIndexingStage;
import com.agentengine.knowledge.core.repository.KnowledgeRepository;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.ConfiguredModel;
import com.agentengine.util.models.config.DefaultModels;
import com.agentengine.util.models.factories.ModelProvider;
import com.agentengine.util.tenancy.Permission;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
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
 * {@link IndexingStage}s their file type needs beyond the knowledge's configured chunking strategy
 * (see {@link #stages}); everything else — assembling and running the pipeline, persisting chunks
 * in batches, sampling a content preview, generating a description — is shared here.
 */
public abstract class AbstractKnowledgeIndexer implements KnowledgeIndexer {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractKnowledgeIndexer.class);
  private static final int PREVIEW_SAMPLE_SIZE = 10;
  private static final int PREVIEW_EXCERPT_LENGTH = 200;
  private static final long DESCRIPTION_TIMEOUT_SECONDS = 8L;
  private static final int INSERT_BATCH_SIZE = 100;

  protected final ChunkingPipelineFactory chunkingPipelineFactory;
  private final KnowledgeRepository knowledgeRepository;
  protected final ModelProvider modelProvider;
  protected final FileService fileService;

  protected AbstractKnowledgeIndexer(
      final ChunkingPipelineFactory chunkingPipelineFactory,
      final KnowledgeRepository knowledgeRepository,
      final ModelProvider modelProvider,
      final FileService fileService) {
    this.chunkingPipelineFactory = chunkingPipelineFactory;
    this.knowledgeRepository = knowledgeRepository;
    this.modelProvider = modelProvider;
    this.fileService = fileService;
  }

  /**
   * The stages, in order, that turn {@code knowledge}'s raw content into chunks — everything before
   * the terminal embedding stage {@link ChunkingPipelineFactory#create} always appends. Defaults to
   * {@link ChunkingPipelineFactory#getConfiguredStages}, the stages implementing the knowledge's
   * own configured chunking strategy; a subclass whose file type needs something beyond that —
   * reading a format no strategy stage can parse directly, or describing embedded media — wraps or
   * replaces this default (see {@code PdfKnowledgeIndexer}, {@code OfficeKnowledgeIndexer}, {@code
   * ImageKnowledgeIndexer}).
   */
  protected abstract List<IndexingStage> stages(final Knowledge knowledge);

  protected abstract RootIndexingStage rootStage(final Knowledge knowledge);

  @Override
  public IndexResult index(final Knowledge knowledge) {
    knowledgeRepository.requirePermission(knowledge.getId(), Permission.EDIT);
    final ChunkingPipeline pipeline =
        chunkingPipelineFactory.create(
            knowledge.getSettings(), rootStage(knowledge), stages(knowledge));

    // Chunks are assigned their grants, persisted in batches, and sampled for the
    // content preview as they arrive — the pipeline's output is never fully materialized here,
    // even though it can run to thousands of embedded (text + vector) chunks. The final count
    // is a running counter, not a list size; the preview sample is a fixed-size reservoir
    // (Algorithm R), not a list looked up by position, since a stream's length isn't known
    // until it ends.
    final AtomicInteger chunkCount = new AtomicInteger();
    final AtomicInteger lastSampledChunkIndex = new AtomicInteger(-1);
    final AtomicInteger sampledCount = new AtomicInteger();
    final List<KnowledgeChunk> sample = new ArrayList<>(PREVIEW_SAMPLE_SIZE);
    pipeline
        .run()
        .doOnNext(
            chunk -> {
              chunkCount.incrementAndGet();
              chunk.setTags(knowledge.getTags());
              chunk.setKnowledgeId(knowledge.getId());
              chunk.setKnowledgeVersion(knowledge.getVersion());
              chunk.setStatus(IndexingStatus.IN_PROGRESS.name());
              // Chunks made from one piece of the item share its index; it is sampled once.
              if (chunk.getChunkIndex() > lastSampledChunkIndex.get()) {
                lastSampledChunkIndex.set(chunk.getChunkIndex());
                reservoirSample(sample, chunk, sampledCount.getAndIncrement());
              }
            })
        .buffer(INSERT_BATCH_SIZE)
        .doOnNext(knowledgeRepository::insertChunks)
        .ignoreElements()
        .blockingAwait();

    final int totalChunks = chunkCount.get();
    LOG.info("Indexed {} chunks for knowledge {}", totalChunks, knowledge.getId());

    final String preview = contentPreview(sample);
    final String generatedDescription =
        StringUtils.isBlank(knowledge.getDescription()) && sample.stream().anyMatch(this::hasContent)
            ? generateDescription(knowledge.getId(), preview)
            : null;
    return new IndexResult(totalChunks, preview, generatedDescription);
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

  /** Whether the chunk has text, a transcript or a description, as opposed to bare media. */
  private boolean hasContent(final KnowledgeChunk chunk) {
    return StringUtils.isNotBlank(chunk.getText())
        || StringUtils.isNotBlank(chunk.getDescription());
  }

  private static String contentPreview(final List<KnowledgeChunk> sampledChunks) {
    final List<String> excerpts = new ArrayList<>(sampledChunks.size());
    for (final KnowledgeChunk chunk : sampledChunks) {
      final StringBuilder excerpt = new StringBuilder();
      if (chunk.getMedia() != null) {
        final String mediaType = mediaTypeLabel(chunk.getMedia());
        excerpt.append("[").append(mediaType);
        if (StringUtils.isNotBlank(chunk.getDescription())) {
          excerpt.append(": ").append(chunk.getDescription().strip());
        }
        if (StringUtils.isNotBlank(chunk.getText())) {
          excerpt.append(" | Text/Transcript: ").append(chunk.getText().strip());
        }
        excerpt.append("]");
      } else if (StringUtils.isNotBlank(chunk.getText())) {
        excerpt.append(chunk.getText().strip());
      }
      if (!excerpt.isEmpty()) {
        final String text = excerpt.toString();
        excerpts.add(
            text.length() <= PREVIEW_EXCERPT_LENGTH
                ? text
                : text.substring(0, PREVIEW_EXCERPT_LENGTH) + "...");
      }
    }
    return String.join("\n[...]\n", excerpts);
  }

  private static String mediaTypeLabel(final FileDetails media) {
    if (media == null) {
      return "Media";
    }
    final String mimeType = media.mimeType();
    if (mimeType != null) {
      final String lower = mimeType.toLowerCase();
      if (lower.startsWith("image/")) {
        return "Image";
      }
      if (lower.startsWith("audio/")) {
        return "Audio";
      }
      if (lower.startsWith("video/")) {
        return "Video";
      }
    }
    return "Media";
  }

  private String generateDescription(final String knowledgeId, final String preview) {
    if (StringUtils.isBlank(preview)) {
      return null;
    }
    try (RefCounted<ConfiguredModel<BaseLlm>> refCounted = modelProvider.get(DefaultModels.FAST_ID)) {
      final String prompt =
          "Excerpts sampled from across a knowledge source (including text, media, or transcripts):\n\n"
              + preview
              + "\n\nIn 1-2 sentences, describe (a) the format and modalities present (e.g. narrative prose, "
              + "technical specifications with architecture diagrams, audio recordings with speech transcripts, "
              + "tabular data), (b) the primary language (e.g. English, Spanish), and (c) what the content is about. "
              + "Output only the description, with no preamble.";
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
