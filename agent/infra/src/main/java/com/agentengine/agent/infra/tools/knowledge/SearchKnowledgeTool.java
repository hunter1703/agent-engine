package com.agentengine.agent.infra.tools.knowledge;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.knowledge.api.beans.IndexingStatus;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.beans.MediaDescription;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.knowledge.api.services.MediaService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.agents.tools.ToolConstructor;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.FileUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.config.DefaultModels;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Modality;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Searches indexed knowledge (text, images, audio) for content semantically relevant to natural
 * language queries or media inputs. When a single non-indexed item is requested, returns its full
 * content instead.
 */
public final class SearchKnowledgeTool extends Tool {

  private static final Logger LOG = LoggerFactory.getLogger(SearchKnowledgeTool.class);

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.SEARCH_KNOWLEDGE,
          """
          Searches indexed knowledge (text, images, audio) for content semantically relevant to your query.
          When a single non-indexed item is specified, returns its full content instead.

          MULTIMODAL QUERYING:
          - Text Query: pass 'query' to search text passages, audio transcripts, and media descriptions.
          - Media Query: pass 'media' (URL or knowledge ID) to find similar media or cross-reference content.
          - Target Modality: pass 'target' (TEXT, IMAGE, AUDIO) to restrict returned modalities. Omit to search all.
          - Multimodal Hybrid: pass both 'query' and 'media' for joint retrieval fused via Reciprocal Rank Fusion (RRF).

          SCOPING & PAGINATION:
          - Pass 'knowledgeIds' to restrict retrieval to specific documents or knowledge collections.
          - Pass 'offset' and 'limit' to page through results.
          """);

  private final KnowledgeService knowledgeService;
  private final FileService fileService;
  private final MediaService mediaService;
  private final ModelProvider modelProvider;

  @ToolConstructor
  public SearchKnowledgeTool(
      final KnowledgeService knowledgeService,
      final FileService fileService,
      final MediaService mediaService,
      final ModelProvider modelProvider) {
    super(DESCRIPTOR);
    this.knowledgeService = knowledgeService;
    this.fileService = fileService;
    this.mediaService = mediaService;
    this.modelProvider = modelProvider;
  }

  public ToolOutput<Map<String, Object>> execute(
      final ToolContext toolContext,
      @ToolArg(
              name = "query",
              description =
                  "Text query to search for (e.g., questions, keywords, phrases said in an audio clip, or descriptions of visual figures).",
              optional = true)
          final String query,
      @ToolArg(
              name = "media",
              description =
                  "Media to search with (URL or knowledge ID). Used to find similar media or retrieve text matching a media sample.",
              optional = true)
          final String media,
      @ToolArg(
              name = "target",
              description =
                  "Target modality to filter results: TEXT (prose, articles, code), IMAGE (charts, diagrams, photos), or AUDIO (speech, recordings). Omit to search across all modalities.",
              optional = true)
          final Modality.Known target,
      @ToolArg(
              name = "knowledgeIds",
              description =
                  "Optional list of knowledge IDs to restrict search to. If a single non-indexed item is passed, its full content is returned.",
              optional = true)
          final List<String> knowledgeIds,
      @ToolArg(name = "limit", description = "Max results to return (default 5).", optional = true)
          final Integer limit,
      @ToolArg(name = "offset", description = "Pagination offset (default 0).", optional = true)
          final Integer offset) {

    final List<String> scopedKnowledgeIds =
        CollectionUtils.nullSafeList(knowledgeIds).stream()
            .filter(StringUtils::isNotBlank)
            .toList();

    if (scopedKnowledgeIds.size() == 1) {
      final Knowledge singleKnowledge = knowledgeService.findById(scopedKnowledgeIds.getFirst());
      if (singleKnowledge == null) {
        return ToolOutput.direct(
            Map.of(
                "error", "Not granted access to knowledge item: " + scopedKnowledgeIds.getFirst()));
      }
      if (IndexingStatus.NON_INDEXED.name().equals(singleKnowledge.getIndexingStatus())) {
        return readFullContent(singleKnowledge);
      }
    }

    final FileDetails queryMediaFile = resolveMedia(media);
    if (StringUtils.isNotBlank(media)) {
      if (queryMediaFile == null) {
        return ToolOutput.direct(
            Map.of(
                "error",
                "Could not resolve media (must be a valid URL or knowledge ID): " + media));
      }
      if (StringUtils.isBlank(queryMediaFile.mimeType())) {
        return ToolOutput.direct(
            Map.of("error", "Unable to determine MIME type for media: " + media));
      }
    }

    if (StringUtils.isBlank(query) && queryMediaFile == null) {
      return ToolOutput.direct(
          Map.of("error", "At least one of 'query' or 'media' is required to search knowledge."));
    }

    final int resolvedOffset = offset != null ? offset : 0;
    final int resolvedLimit = limit != null ? limit : 5;

    final boolean targetAll = target == null || target == Modality.Known.MODALITY_UNSPECIFIED;
    final boolean targetText = targetAll || target == Modality.Known.TEXT;
    final boolean targetImage = targetAll || target == Modality.Known.IMAGE;
    final boolean targetAudio = targetAll || target == Modality.Known.AUDIO;

    final List<Filter> semanticSubFilters = new ArrayList<>();

    if (StringUtils.isNotBlank(query)) {
      if (targetText) {
        semanticSubFilters.add(semanticFilter(KnowledgeChunk.FIELD_TEXT, query, DefaultModels.TEXT_EMBEDDING_ID));
      }
      if (targetImage || targetAudio) {
        semanticSubFilters.add(semanticFilter(
                KnowledgeChunk.FIELD_DESCRIPTION, query, DefaultModels.TEXT_EMBEDDING_ID));
      }
    }

    if (queryMediaFile != null) {
      final Modality.Known queryMediaModality = detectModality(queryMediaFile);
      if (queryMediaModality == Modality.Known.TEXT) {
        if ((targetAll || targetText) && StringUtils.isBlank(query)) {
          final String textContent = readMediaAsText(queryMediaFile);
          if (StringUtils.isNotBlank(textContent)) {
            semanticSubFilters.add(semanticFilter(
                    KnowledgeChunk.FIELD_TEXT, textContent, DefaultModels.TEXT_EMBEDDING_ID));
          }
        }
      } else {
        if (targetAll || target == queryMediaModality) {
          semanticSubFilters.add(
              semanticFilter(
                  KnowledgeChunk.FIELD_MEDIA, queryMediaFile, DefaultModels.IMAGE_EMBEDDING_ID));
        }
        if (targetText && StringUtils.isBlank(query)) {
          final String mediaDescription = describeMedia(queryMediaFile);
          if (StringUtils.isNotBlank(mediaDescription)) {
            semanticSubFilters.add(
                semanticFilter(
                    KnowledgeChunk.FIELD_TEXT, mediaDescription, DefaultModels.TEXT_EMBEDDING_ID));
          }
        }
      }
    }

    if (semanticSubFilters.isEmpty()) {
      return ToolOutput.direct(Map.of("error", "No valid search criteria provided."));
    }

    final Filter semanticFilter =
        semanticSubFilters.size() == 1
            ? semanticSubFilters.getFirst()
            : Filters.or(semanticSubFilters.toArray(new Filter[0]));
    final Filter filter =
        scopedKnowledgeIds.isEmpty()
            ? semanticFilter
            : Filters.and(
                semanticFilter, Filters.in(KnowledgeChunk.FIELD_KNOWLEDGE_ID, scopedKnowledgeIds));

    final Query searchQuery =
        new Query().withFilter(filter).withPage(new Page(resolvedOffset, resolvedLimit));

    final List<KnowledgeChunk> chunks = knowledgeService.search(searchQuery).getItems();
    final Map<String, Object> output = new LinkedHashMap<>();
    output.put("chunks", chunks);
    output.put("offset", resolvedOffset);
    output.put("limit", resolvedLimit);
    output.put("hasMore", chunks.size() >= resolvedLimit);
    return ToolOutput.direct(output);
  }

  /**
   * A semantic search of {@code field} for {@code value}, embedded by the model {@code
   * defaultModelId} stands for now, that only matches the vectors of that model.
   */
  private Filter semanticFilter(
      final String field, final Object value, final String defaultModelId) {
    final String modelId = modelProvider.resolveEmbeddingModelId(defaultModelId);
    return Filters.and(
        Filters.semanticSearch(field, value)
            .withAdditional(Map.of(Filters.EMBEDDING_MODEL_ID, modelId)),
        Filters.eq(KnowledgeChunk.FIELD_EMBEDDER, modelId));
  }

  private FileDetails resolveMedia(final String mediaRef) {
    if (StringUtils.isBlank(mediaRef)) {
      return null;
    }
    if (mediaRef.startsWith("http://") || mediaRef.startsWith("https://")) {
      return fileService.withDetectedMimeType(FileDetails.fromUrl(mediaRef));
    }
    final Knowledge knowledge = knowledgeService.findById(mediaRef);
    return knowledge == null ? null : knowledge.getFileDetails();
  }

  private static Modality.Known detectModality(final FileDetails fileDetails) {
    if (fileDetails == null || StringUtils.isBlank(fileDetails.mimeType())) {
      return Modality.Known.TEXT;
    }
    final String lower = fileDetails.mimeType().toLowerCase();
    if (lower.startsWith("audio/")) {
      return Modality.Known.AUDIO;
    }
    if (lower.startsWith("image/")) {
      return Modality.Known.IMAGE;
    }
    return Modality.Known.TEXT;
  }

  private String readMediaAsText(final FileDetails fileDetails) {
    try (InputStream stream = fileService.getContent(fileDetails)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch (final IOException | IllegalStateException exception) {
      LOG.debug("Failed to read text media content", exception);
      return null;
    }
  }

  private String describeMedia(final FileDetails mediaFile) {
    if (mediaFile == null || StringUtils.isBlank(mediaFile.mimeType())) {
      return null;
    }
    try {
      final MediaDescription mediaDescription = mediaService.describe(mediaFile);
      if (mediaDescription == null) {
        return null;
      }
      final String description = mediaDescription.description();
      final String transcript = mediaDescription.transcript();
      if (StringUtils.isNotBlank(description) && StringUtils.isNotBlank(transcript)) {
        return description + " " + transcript;
      }
      return StringUtils.isNotBlank(description) ? description : transcript;
    } catch (final Exception exception) {
      LOG.debug("Failed to describe media for cross-modal search", exception);
      return null;
    }
  }

  private ToolOutput<Map<String, Object>> readFullContent(final Knowledge knowledge) {
    final FileDetails fileDetails = knowledge.getFileDetails();
    if (fileDetails == null) {
      return ToolOutput.direct(
          Map.of(
              Constants.ToolStatus.STATUS,
              "failed",
              "error",
              "Knowledge has no associated file details"));
    }
    final boolean isText = FileUtils.isTextFile(fileDetails);
    try (InputStream stream = fileService.getContent(fileDetails)) {
      final byte[] bytes = stream.readAllBytes();
      final String value =
          isText
              ? new String(bytes, StandardCharsets.UTF_8)
              : Base64.getEncoder().encodeToString(bytes);
      return ToolOutput.direct(
          Map.of(
              Constants.ToolStatus.STATUS,
              Constants.ToolStatus.SUCCESS,
              "content",
              value,
              "encoding",
              isText ? "utf-8" : "base64",
              "mime_type",
              fileDetails.mimeType() == null ? "" : fileDetails.mimeType()));
    } catch (final IllegalStateException exception) {
      return ToolOutput.direct(
          Map.of(
              Constants.ToolStatus.STATUS,
              "failed",
              "error",
              "Failed to read content for knowledge file"));
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
