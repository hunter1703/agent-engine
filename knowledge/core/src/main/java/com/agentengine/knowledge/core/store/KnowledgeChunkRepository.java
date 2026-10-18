package com.agentengine.knowledge.core.store;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.AbstractRepository;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.vectordb.VectorBackend;
import com.agentengine.util.vectordb.VectorEntityStore;
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

  private final VectorEntityStore<KnowledgeChunk> vectorStore;

  @Inject
  public KnowledgeChunkRepository(
      final VectorBackend vectorBackend, final ValidationService validationService) {
    this(
        vectorBackend.getEntityStore(
            new VectorRepositorySpec<>(
                COLLECTION_NAME,
                KnowledgeChunk.class,
                KnowledgeVectorStoreClientType.KNOWLEDGE,
                KnowledgeChunkRepository::toPayload,
                KnowledgeChunkRepository::fromPayload)),
        validationService);
  }

  private KnowledgeChunkRepository(
      final VectorEntityStore<KnowledgeChunk> vectorStore,
      final ValidationService validationService) {
    super(vectorStore, validationService);
    this.vectorStore = vectorStore;
  }

  /** The similarity of each tag of the chunks {@code query} matches; see the vector store. */
  public Map<String, Double> findTagSimilarities(
      final Query query, final int maxTags, final int closestChunksPerTag) {
    return vectorStore.findTagSimilarities(query, maxTags, closestChunksPerTag);
  }

  private static Map<String, Object> toPayload(final KnowledgeChunk chunk) {
    final Map<String, Object> payload = new HashMap<>();
    payload.put(KnowledgeChunk.FIELD_KNOWLEDGE_ID, chunk.getKnowledgeId());
    payload.put(KnowledgeChunk.FIELD_CHUNK_INDEX, chunk.getChunkIndex());
    payload.put(KnowledgeChunk.FIELD_KNOWLEDGE_VERSION, chunk.getKnowledgeVersion());
    payload.put(KnowledgeChunk.FIELD_STATUS, chunk.getStatus());
    if (chunk.getText() != null) {
      payload.put(KnowledgeChunk.FIELD_TEXT, chunk.getText());
    }
    payload.put(KnowledgeChunk.FIELD_CHUNK_START, chunk.getChunkStart());
    payload.put(KnowledgeChunk.FIELD_CHUNK_END, chunk.getChunkEnd());

    if (chunk.getDescription() != null) {
      payload.put(KnowledgeChunk.FIELD_DESCRIPTION, chunk.getDescription());
    }
    if (chunk.getMedia() != null) {
      payload.put(KnowledgeChunk.FIELD_MEDIA, JsonUtils.toMap(chunk.getMedia()));
    }
    if (chunk.getEmbedder() != null) {
      payload.put(KnowledgeChunk.FIELD_EMBEDDER, chunk.getEmbedder());
    }
    if (CollectionUtils.isNotEmpty(chunk.getTags())) {
      payload.put(BaseEntity.FIELD_TAGS, chunk.getTags());
    }
    return payload;
  }

  private static KnowledgeChunk fromPayload(final Map<String, Object> payload) {
    final KnowledgeChunk chunk = new KnowledgeChunk();
    chunk.setKnowledgeId(
        CollectionUtils.getStringValueFromMap(payload, KnowledgeChunk.FIELD_KNOWLEDGE_ID));
    chunk.setChunkIndex(
        CollectionUtils.getIntValueFromMap(payload, KnowledgeChunk.FIELD_CHUNK_INDEX, 0));
    final Number version = (Number) payload.get(KnowledgeChunk.FIELD_KNOWLEDGE_VERSION);
    chunk.setKnowledgeVersion(version != null ? version.longValue() : null);
    chunk.setStatus(CollectionUtils.getStringValueFromMap(payload, KnowledgeChunk.FIELD_STATUS));
    chunk.setText(CollectionUtils.getStringValueFromMap(payload, KnowledgeChunk.FIELD_TEXT));
    chunk.setChunkStart(
        CollectionUtils.getIntValueFromMap(payload, KnowledgeChunk.FIELD_CHUNK_START, 0));
    chunk.setChunkEnd(
        CollectionUtils.getIntValueFromMap(payload, KnowledgeChunk.FIELD_CHUNK_END, 0));
    chunk.setDescription(
        CollectionUtils.getStringValueFromMap(payload, KnowledgeChunk.FIELD_DESCRIPTION));
    chunk.setEmbedder(
        CollectionUtils.getStringValueFromMap(payload, KnowledgeChunk.FIELD_EMBEDDER));
    chunk.setTags(CollectionUtils.getListFromMap(payload, BaseEntity.FIELD_TAGS));

    final Object mediaObj = payload.get(KnowledgeChunk.FIELD_MEDIA);
    if (mediaObj instanceof FileDetails fileDetails) {
      chunk.setMedia(fileDetails);
    } else {
      //noinspection unchecked
      chunk.setMedia(JsonUtils.fromMap((Map<String, Object>) mediaObj, FileDetails.class));
    }
    return chunk;
  }
}
