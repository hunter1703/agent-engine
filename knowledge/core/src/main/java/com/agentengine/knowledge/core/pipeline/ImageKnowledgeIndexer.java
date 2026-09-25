package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.FileUtils;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.models.factories.Model;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

/**
 * Indexes image knowledge by describing it with the configured vision model before chunking — the
 * description is wrapped in {@code <image>...</image>} so a search result (or an agent reading one)
 * can tell at a glance that a chunk describes an image rather than being extracted text, without a
 * separate typed field on {@link com.agentengine.knowledge.api.beans.KnowledgeChunk}. The same
 * wrap-a-generated-description-in-a-tag approach is the natural way to extend to other binary media
 * (audio, video) later, each under its own tag.
 */
@Singleton
public class ImageKnowledgeIndexer extends AbstractTextKnowledgeIndexer {

  private static final int PRIORITY = 100;
  private static final String DESCRIPTION_PROMPT =
      "Describe this image in detail for a search index: its subject, setting, colors, mood, and "
          + "any visible text. Output only the description, with no preamble.";

  @Inject
  public ImageKnowledgeIndexer(
      final ChunkingPipelineFactory chunkingPipelineFactory,
      final KnowledgeChunkStore vectorStore,
      final FileService fileService,
      final DefaultModelsRepository defaultModelsRepository,
      final ModelProvider modelProvider) {
    super(
        chunkingPipelineFactory, vectorStore, fileService, defaultModelsRepository, modelProvider);
  }

  @Override
  public boolean canIndex(final Knowledge knowledge) {
    return FileUtils.isImageFile(knowledge.getFileDetails());
  }

  @Override
  protected Reader read(final Knowledge knowledge, final InputStream content) throws IOException {
    return new StringReader("<image>" + describe(knowledge, content) + "</image>");
  }

  private String describe(final Knowledge knowledge, final InputStream content) throws IOException {
    final byte[] imageBytes = content.readAllBytes();
    final String mimeType = knowledge.getFileDetails().mimeType();
    final KnowledgeSettings settings = knowledge.getSettings();
    final String visionModelId =
        settings != null && StringUtils.isNotBlank(settings.getVisionModelId())
            ? settings.getVisionModelId()
            : defaultModelsRepository.getVisionModelId();

    final Content requestContent =
        Content.builder()
            .role(Constants.AUTHOR_USER)
            .parts(List.of(Part.fromText(DESCRIPTION_PROMPT), Part.fromBytes(imageBytes, mimeType)))
            .build();
    final LlmRequest request = LlmRequest.builder().contents(List.of(requestContent)).build();

    try (RefCounted<Model.LLMModel> refCounted = modelProvider.get(visionModelId)) {
      final LlmResponse response =
          refCounted.value().model().generateContent(request, false).blockingSingle();
      return response.content().map(Content::text).map(String::trim).orElse("");
    }
  }

  @Override
  public int priority() {
    return PRIORITY;
  }
}
