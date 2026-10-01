package com.agentengine.util.vectordb;

/**
 * A vector database that repositories keep their entities in: each registers the collection it
 * keeps its entities in, and all registered collections are set up together.
 */
public interface VectorBackend {

  /** Registers a repository's collection, set up along with the others from now on. */
  <T extends VectorEntity> VectorEntityStore<T> getEntityStore(VectorRepositorySpec<T> spec);

  /**
   * Creates the registered collections of the {@code clientType} store for customer {@code
   * customerId}.
   */
  void setup(VectorStoreClientType clientType, String customerId);
}
