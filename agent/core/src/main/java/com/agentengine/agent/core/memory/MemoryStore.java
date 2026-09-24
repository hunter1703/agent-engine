package com.agentengine.agent.core.memory;

import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.models.factories.Model;
import com.agentengine.util.models.factories.ModelProvider;
import com.agentengine.util.vectordb.QdrantVectorStore;
import com.agentengine.util.vectordb.VectorDbClientFactory;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Qdrant-backed vector store for {@link Memory}.
 *
 * <p>Stores and semantically retrieves persistent user memories scoped by agent and user.
 */
@Singleton
public class MemoryStore extends QdrantVectorStore<Memory> {

  private final ModelProvider modelProvider;
  private final DefaultModelsRepository defaultModelsRepository;

  @Inject
  public MemoryStore(
      final VectorDbClientFactory clientFactory,
      final ModelProvider modelProvider,
      final DefaultModelsRepository defaultModelsRepository) {
    super(
        AssetClass.MEMORY,
        Memory.class,
        AgentVectorStoreClientType.MEMORY,
        clientFactory,
        (modelId, query) -> {
          try (RefCounted<Model.EmbeddingModel> refCounted =
              modelProvider.getEmbeddingModel(modelId)) {
            return refCounted.value().model().embed(query).content().vector();
          }
        });
    this.modelProvider = modelProvider;
    this.defaultModelsRepository = defaultModelsRepository;
  }

  @Override
  public Memory save(final Memory memory) {
    if (memory != null) {
      embedAll(List.of(memory));
    }
    return super.save(memory);
  }

  @Override
  public List<Memory> insertMany(final List<Memory> memories) {
    if (CollectionUtils.isNotEmpty(memories)) {
      embedAll(memories);
    }
    return super.insertMany(memories);
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

  private void embedAll(final List<Memory> memories) {
    final String textVectorName = getFieldVsVectorName().get(Memory.FIELD_TEXT);
    final List<Memory> toEmbed =
        memories.stream()
            .filter(memory -> memory != null && StringUtils.isNotBlank(memory.getText()))
            .toList();
    if (toEmbed.isEmpty()) {
      return;
    }
    final String modelId = defaultModelsRepository.getEmbeddingModelId();
    if (StringUtils.isBlank(modelId)) {
      throw new IllegalStateException("No default embedding model configured for MemoryStore");
    }
    try (final RefCounted<Model.EmbeddingModel> refCounted =
        modelProvider.getEmbeddingModel(modelId)) {
      final Model.EmbeddingModel model = refCounted.value();
      final EmbeddingModel embeddingModel = model.model();
      for (final List<Memory> batch : CollectionUtils.batches(toEmbed, model.maxBatchSize())) {
        final List<TextSegment> segments =
            batch.stream().map(memory -> TextSegment.from(memory.getText())).toList();
        final Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        final List<Embedding> embeddings = response.content();
        final Iterator<Memory> memoryIterator = batch.iterator();
        final Iterator<Embedding> embeddingIterator = embeddings.iterator();
        while (memoryIterator.hasNext() && embeddingIterator.hasNext()) {
          memoryIterator.next().setVector(textVectorName, embeddingIterator.next().vector());
        }
      }
    }
  }
}
