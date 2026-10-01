package com.agentengine.knowledge.core.store;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.repository.AbstractRepository;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.models.factories.Model;
import com.agentengine.util.models.factories.ModelProvider;
import com.agentengine.util.vectordb.VectorBackend;
import com.agentengine.util.vectordb.VectorRepositorySpec;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * The vector store of {@link KnowledgeChunk}s. Chunks carry no access of their own: {@code
 * KnowledgeRepository} reaches them only for knowledge the caller holds the access on.
 */
@Singleton
@Startup
public class KnowledgeChunkRepository extends AbstractRepository<KnowledgeChunk> {

  private static final String COLLECTION_NAME = "KnowledgeChunk";

  public static final String KEY_KNOWLEDGE_ID = KnowledgeChunk.FIELD_KNOWLEDGE_ID;
  public static final String KEY_AGENT_ID = KnowledgeChunk.FIELD_AGENT_ID;
  public static final String KEY_CHUNK_INDEX = "chunkIndex";
  public static final String KEY_TEXT = "text";
  public static final String KEY_CHUNK_START = "chunkStart";
  public static final String KEY_CHUNK_END = "chunkEnd";

  @Inject
  public KnowledgeChunkRepository(
      final VectorBackend vectorBackend,
      final ModelProvider modelProvider,
      final ValidationService validationService) {
    super(
        vectorBackend.getEntityStore(
            new VectorRepositorySpec<>(
                COLLECTION_NAME,
                KnowledgeChunk.class,
                KnowledgeVectorStoreClientType.KNOWLEDGE,
                KnowledgeChunkRepository::toPayload,
                KnowledgeChunkRepository::fromPayload,
                (modelId, query) -> {
                  try (RefCounted<Model.EmbeddingModel> refCounted =
                      modelProvider.getEmbeddingModel(modelId)) {
                    return refCounted.value().model().embed(query).content().vector();
                  }
                })),
        validationService);
  }

  private static Map<String, Object> toPayload(final KnowledgeChunk chunk) {
    final Map<String, Object> payload = new HashMap<>();
    payload.put(KEY_KNOWLEDGE_ID, chunk.getKnowledgeId());
    payload.put(KEY_AGENT_ID, chunk.getAgentId());
    payload.put(KEY_CHUNK_INDEX, chunk.getChunkIndex());
    payload.put(KEY_TEXT, chunk.getText());
    payload.put(KEY_CHUNK_START, chunk.getChunkStart());
    payload.put(KEY_CHUNK_END, chunk.getChunkEnd());
    return payload;
  }

  private static KnowledgeChunk fromPayload(final Map<String, Object> payload) {
    final KnowledgeChunk chunk = new KnowledgeChunk();
    chunk.setKnowledgeId(CollectionUtils.getStringValueFromMap(payload, KEY_KNOWLEDGE_ID));
    chunk.setAgentId(CollectionUtils.getStringValueFromMap(payload, KEY_AGENT_ID));
    chunk.setChunkIndex(CollectionUtils.getIntValueFromMap(payload, KEY_CHUNK_INDEX, 0));
    chunk.setText(CollectionUtils.getStringValueFromMap(payload, KEY_TEXT));
    chunk.setChunkStart(CollectionUtils.getIntValueFromMap(payload, KEY_CHUNK_START, 0));
    chunk.setChunkEnd(CollectionUtils.getIntValueFromMap(payload, KEY_CHUNK_END, 0));
    return chunk;
  }
}
