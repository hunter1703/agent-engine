package com.agentengine.agent.core.memory;

import com.agentengine.agent.infra.factories.model.EmbeddingModelFactory;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.vectordb.QdrantVectorStore;
import com.agentengine.util.vectordb.VectorDbClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Qdrant-backed vector store for {@link Memory}.
 *
 * <p>Stores and semantically retrieves persistent user memories scoped by agent and user.
 */
@Singleton
public class MemoryStore extends QdrantVectorStore<Memory> {

  @Inject
  public MemoryStore(
      final VectorDbClientFactory clientFactory,
      final EmbeddingModelFactory embeddingModelFactory) {
    super(
        AssetClass.MEMORY,
        clientFactory,
        (modelId, query) -> embeddingModelFactory.get(modelId).embed(query).content().vector());
  }

  @Override
  protected Map<String, Object> toPayload(final Memory memory) {
    final Map<String, Object> payload = new HashMap<>();
    payload.put(Memory.FIELD_AGENT_ID, memory.getAgentId());
    payload.put(Memory.FIELD_USER_ID, memory.getUserId());
    payload.put(Memory.FIELD_TEXT, memory.getText());
    return payload;
  }

  @Override
  protected Memory fromPayload(final Map<String, Object> payload) {
    final Memory memory = new Memory();
    memory.setAgentId(strValue(payload, Memory.FIELD_AGENT_ID));
    memory.setUserId(strValue(payload, Memory.FIELD_USER_ID));
    memory.setText(strValue(payload, Memory.FIELD_TEXT));
    return memory;
  }
}
