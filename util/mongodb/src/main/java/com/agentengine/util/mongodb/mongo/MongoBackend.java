package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.repository.DocumentStoreClientType;
import com.agentengine.util.common.repository.EntityStore;
import com.agentengine.util.context.Context;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The {@link DocumentBackend} kept in MongoDB. */
@Singleton
public class MongoBackend implements DocumentBackend {

  private final MongoClientFactory clientFactory;
  private final List<MongoEntityStore<?>> entityStores = new CopyOnWriteArrayList<>();

  @Inject
  public MongoBackend(final MongoClientFactory clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public <T extends BaseEntity> EntityStore<T> getEntityStore(
      final DocumentRepositorySpec<T> spec) {
    final MongoEntityStore<T> entityStore = new MongoEntityStore<>(spec, clientFactory);
    entityStores.add(entityStore);
    return entityStore;
  }

  @Override
  public void setup(final DocumentStoreClientType clientType, final String customerId) {
    for (final MongoEntityStore<?> entityStore : entityStores) {
      final DocumentRepositorySpec<?> spec = entityStore.spec();
      if (spec.clientType().name().equals(clientType.name())
          && spec.global() == (customerId == null)) {
        if (customerId == null || customerId.equals(Context.currentCustomerId().orElse(null))) {
          entityStore.setup();
        } else {
          Context.asSystemUser(customerId).run(entityStore::setup);
        }
      }
    }
  }
}
