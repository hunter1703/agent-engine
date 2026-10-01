package com.agentengine.util.vectordb;

import com.agentengine.util.context.Context;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The {@link VectorBackend} kept in Qdrant. */
@Singleton
public class QdrantBackend implements VectorBackend {

  private final VectorDbClientFactory clientFactory;
  private final List<QdrantEntityStore<?>> entityStores = new CopyOnWriteArrayList<>();

  @Inject
  public QdrantBackend(final VectorDbClientFactory clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public <T extends VectorEntity> VectorEntityStore<T> getEntityStore(
      final VectorRepositorySpec<T> spec) {
    final QdrantEntityStore<T> entityStore = new QdrantEntityStore<>(spec, clientFactory);
    entityStores.add(entityStore);
    return entityStore;
  }

  @Override
  public void setup(final VectorStoreClientType clientType, final String customerId) {
    if (customerId == null) {
      throw new IllegalArgumentException("Vector collections always belong to a customer");
    }
    for (final QdrantEntityStore<?> entityStore : entityStores) {
      if (entityStore.spec().clientType().name().equals(clientType.name())) {
        if (customerId.equals(Context.currentCustomerId().orElse(null))) {
          entityStore.setup();
        } else {
          Context.asSystemUser(customerId).run(entityStore::setup);
        }
      }
    }
  }
}
