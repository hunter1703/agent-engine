package com.agentengine.knowledge.core.store;

import com.agentengine.agent.infra.factories.model.EmbeddingModelFactory;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.vectordb.QdrantVectorStore;
import com.agentengine.util.vectordb.VectorDbClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Qdrant-backed vector store for {@link KnowledgeChunk}.
 *
 * <p>Extends the generic {@link QdrantVectorStore} with knowledge-chunk-specific serialization and
 * deserialization logic.
 */
@Singleton
public class KnowledgeChunkStore extends QdrantVectorStore<KnowledgeChunk> {

  public static final String KEY_KNOWLEDGE_ID = KnowledgeChunk.FIELD_KNOWLEDGE_ID;
  public static final String KEY_AGENT_ID = KnowledgeChunk.FIELD_AGENT_ID;
  public static final String KEY_CHUNK_INDEX = "chunkIndex";
  public static final String KEY_TEXT = "text";
  public static final String KEY_CHUNK_START = "chunkStart";
  public static final String KEY_CHUNK_END = "chunkEnd";

  @Inject
  public KnowledgeChunkStore(
      final VectorDbClientFactory clientFactory,
      final EmbeddingModelFactory embeddingModelFactory) {
    super(
        AssetClass.KNOWLEDGE_CHUNK,
        clientFactory,
        (modelId, query) -> embeddingModelFactory.get(modelId).embed(query).content().vector());
  }

  @Override
  protected Map<String, Object> toPayload(final KnowledgeChunk chunk) {
    final Map<String, Object> payload = new HashMap<>();
    payload.put(KEY_KNOWLEDGE_ID, chunk.getKnowledgeId());
    payload.put(KEY_AGENT_ID, chunk.getAgentId());
    payload.put(KEY_CHUNK_INDEX, chunk.getChunkIndex());
    payload.put(KEY_TEXT, chunk.getText());
    payload.put(KEY_CHUNK_START, chunk.getChunkStart());
    payload.put(KEY_CHUNK_END, chunk.getChunkEnd());
    return payload;
  }

  @Override
  protected KnowledgeChunk fromPayload(final Map<String, Object> payload) {
    final KnowledgeChunk chunk = new KnowledgeChunk();
    chunk.setKnowledgeId(strValue(payload, KEY_KNOWLEDGE_ID));
    chunk.setAgentId(strValue(payload, KEY_AGENT_ID));
    chunk.setChunkIndex(intVal(payload, KEY_CHUNK_INDEX));
    chunk.setText(strValue(payload, KEY_TEXT));
    chunk.setChunkStart(intVal(payload, KEY_CHUNK_START));
    chunk.setChunkEnd(intVal(payload, KEY_CHUNK_END));
    return chunk;
  }
}
