package com.agentengine.util.vectordb;

import com.agentengine.util.common.repository.EntityStore;

/**
 * The vector storage of one entity class, from a {@link VectorBackend}. A semantic search filter's
 * text is embedded with the model its {@value #FIELD_EMBEDDING_MODEL_ID} additional names, or the
 * one the whole filter's additional names.
 */
public interface VectorEntityStore<T extends VectorEntity> extends EntityStore<T> {

  String FIELD_EMBEDDING_MODEL_ID = "embeddingModelId";

  /** The name of the vector the entity's {@code field} is embedded into. */
  String vectorName(String field);
}
