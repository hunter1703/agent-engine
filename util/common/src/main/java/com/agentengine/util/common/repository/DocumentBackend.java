package com.agentengine.util.common.repository;

import com.agentengine.util.common.beans.BaseEntity;

/**
 * A document database that repositories keep their entities in: each registers the collection it
 * keeps its entities in, and all registered collections are set up together.
 */
public interface DocumentBackend {

  /** Registers a repository's collection, set up along with the others from now on. */
  <T extends BaseEntity> EntityStore<T> getEntityStore(DocumentRepositorySpec<T> spec);

  /**
   * Creates the indexes of the registered collections of the {@code clientType} store: those of
   * customer {@code customerId}'s own database, or the global ones when it is null.
   */
  void setup(DocumentStoreClientType clientType, String customerId);
}
