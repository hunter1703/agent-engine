package com.agentengine.agent.infra.tools.knowledge;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
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
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
@DiscoverableTool
public final class SearchKnowledgeTool extends Tool {

  private static final Logger LOG = LoggerFactory.getLogger(SearchKnowledgeTool.class);

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.SEARCH_KNOWLEDGE,
          """
          Searches knowledge by meaning, with a text query, a media input, or both.
          Returns: { chunks, next_offset? } for a search, where next_offset is present while more results remain; \
          { content, mime_type, total_chars? } for a non-indexed text item, where total_chars is present when the content was cut short; \
          { knowledge_id, mime_type, attached: true } for a non-indexed image, audio or other binary item, which is shown to you right after the result; \
          or { error } on failure.""");

  private static final int CHUNKS_PER_PAGE = 5;
  private static final int MAX_NON_INDEXED_KNOWLEDGE_CHUNK_TEXT_CHARS = 50_000;
  /** Marks a result whose item is shown to the model right after it; see {@code ToolResultPlugin}. */
  public static final String ATTACHED = "attached";

  public static final int MAX_ATTACHED_BYTES = 5_000_000;
  private final KnowledgeService knowledgeService;
  private final FileService fileService;
  private final MediaService mediaService;
  private final ModelProvider modelProvider;

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
                  """
                  Natural language text query to search for (e.g. questions, keywords, domain terms). \
                  Can be combined with 'media' for joint multimodal hybrid search.""",
              optional = true)
          final String query,
      @ToolArg(
              name = "media",
              description =
                  """
                  Media input (URL or knowledge ID) to search with. Can be combined with 'query' \
                  for joint multimodal hybrid search.""",
              optional = true)
          final String media,
      @ToolArg(
              name = "target",
              description =
                  "Target modality to filter results (TEXT, IMAGE, AUDIO). Omit to search across all modalities.",
              optional = true)
          final Modality.Known target,
      @ToolArg(
              name = Constants.ToolArgs.KNOWLEDGE_IDS,
              description =
                  """
                  Knowledge ids to search within; omit to search all indexed knowledge. A single id of a \
                  non-indexed item cannot be searched, so its full content is returned.""",
              optional = true)
          final List<String> knowledgeIds,
      @ToolArg(
              name = "offset",
              description =
                  """
                  0-indexed pagination offset (default 0). When a prior response returned next_offset, \
                  pass it to fetch the subsequent page of results.""",
              optional = true)
          final Integer offset,
      @ToolArg(
              name = "response_format",
              description =
                  """
                  'concise' (default) gives 200-character previews, to survey what matches; 'detailed' \
                  gives full text, to read exact passages.""",
              enums = {"concise", "detailed"},
              optional = true)
          final String responseFormat) {

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

    final boolean targetAll = target == null || target == Modality.Known.MODALITY_UNSPECIFIED;
    final boolean targetText = targetAll || target == Modality.Known.TEXT;
    final boolean targetImage = targetAll || target == Modality.Known.IMAGE;
    final boolean targetAudio = targetAll || target == Modality.Known.AUDIO;

    final List<Filter> semanticSubFilters = new ArrayList<>();

    if (StringUtils.isNotBlank(query)) {
      if (targetText) {
        semanticSubFilters.add(
            semanticFilter(KnowledgeChunk.FIELD_TEXT, query, DefaultModels.TEXT_EMBEDDING_ID));
      }
      if (targetImage || targetAudio) {
        semanticSubFilters.add(
            semanticFilter(
                KnowledgeChunk.FIELD_DESCRIPTION, query, DefaultModels.TEXT_EMBEDDING_ID));
      }
    }

    if (queryMediaFile != null) {
      final Modality.Known queryMediaModality = detectModality(queryMediaFile);
      if (queryMediaModality == Modality.Known.TEXT) {
        if ((targetAll || targetText) && StringUtils.isBlank(query)) {
          final String textContent = readMediaAsText(queryMediaFile);
          if (StringUtils.isNotBlank(textContent)) {
            semanticSubFilters.add(
                semanticFilter(
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
        new Query().withFilter(filter).withPage(new Page(resolvedOffset, CHUNKS_PER_PAGE));

    final List<KnowledgeChunk> chunks = knowledgeService.search(searchQuery).getItems();
    final boolean isDetailed = "detailed".equalsIgnoreCase(responseFormat);
    final List<Map<String, Object>> chunkMaps =
        CollectionUtils.nullSafeList(chunks).stream()
            .map(chunk -> toChunkMap(chunk, isDetailed))
            .toList();
    final Map<String, Object> output = new LinkedHashMap<>();
    output.put("chunks", chunkMaps);
    if (chunks.size() >= CHUNKS_PER_PAGE) {
      output.put("next_offset", resolvedOffset + chunks.size());
    }
    return ToolOutput.direct(output);
  }

  private static Map<String, Object> toChunkMap(
      final KnowledgeChunk chunk, final boolean isDetailed) {
    final Map<String, Object> map = new LinkedHashMap<>();
    if (StringUtils.isNotBlank(chunk.getKnowledgeId())) {
      map.put(Constants.ToolArgs.KNOWLEDGE_ID, chunk.getKnowledgeId());
    }
    if (StringUtils.isNotBlank(chunk.getText())) {
      final String text = chunk.getText();
      if (!isDetailed && text.length() > 200) {
        map.put("text", text.substring(0, 200) + "...");
      } else {
        map.put("text", text);
      }
    }
    if (StringUtils.isNotBlank(chunk.getDescription())) {
      final String description = chunk.getDescription();
      if (!isDetailed && description.length() > 200) {
        map.put("description", description.substring(0, 200) + "...");
      } else {
        map.put("description", description);
      }
    }
    if (chunk.getMedia() != null) {
      map.put("media", chunk.getMedia());
    }
    map.put("chunk_index", chunk.getChunkIndex());
    return map;
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
      return ToolOutput.direct(Map.of("error", "Knowledge has no associated file details"));
    }
    final String mimeType = fileDetails.mimeType() == null ? "" : fileDetails.mimeType();
    try (InputStream stream = fileService.getContent(fileDetails)) {
      if (!FileUtils.isTextFile(fileDetails)) {
        if (fileDetails.size() > MAX_ATTACHED_BYTES) {
          return ToolOutput.direct(
              Map.of(
                  "error",
                  "Binary asset (%s, %d bytes) is too large to show. Search it with the media argument."
                      .formatted(mimeType, fileDetails.size())));
        }
        return ToolOutput.direct(
            Map.of(Constants.ToolArgs.KNOWLEDGE_ID, knowledge.getId(), "mime_type", mimeType, ATTACHED, true));
      }

      final String fullText = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      final Map<String, Object> response = new LinkedHashMap<>();
      if (fullText.length() > MAX_NON_INDEXED_KNOWLEDGE_CHUNK_TEXT_CHARS) {
        response.put(
            "content",
            fullText.substring(0, MAX_NON_INDEXED_KNOWLEDGE_CHUNK_TEXT_CHARS)
                + "\n...<truncated at "
                + MAX_NON_INDEXED_KNOWLEDGE_CHUNK_TEXT_CHARS
                + " chars>...");
        response.put("total_chars", fullText.length());
      } else {
        response.put("content", fullText);
      }
      response.put("mime_type", mimeType);
      return ToolOutput.direct(response);
    } catch (final IllegalStateException exception) {
      return ToolOutput.direct(Map.of("error", "Failed to read content for knowledge file"));
    } catch (final IOException exception) {
      return ToolOutput.direct(
          Map.of("error", "Failed to read content for knowledge file: " + exception.getMessage()));
    }
  }
}
