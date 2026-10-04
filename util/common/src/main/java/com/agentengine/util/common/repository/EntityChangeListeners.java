package com.agentengine.util.common.repository;

import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.utils.CollectionUtils;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Every {@link EntityChangeListener} bean, by the entity class it listens to. */
@Singleton
public class EntityChangeListeners {

  private static final Logger LOG = LoggerFactory.getLogger(EntityChangeListeners.class);

  // Resolved on first use, since a listener may depend on a bean whose repository publishes here.
  private final LazyLoader<Map<Class<?>, List<EntityChangeListener<?>>>> entityClassVsListeners;
  private final LazyLoader<List<GlobalEntityChangeListener>> globalListeners;

  @Inject
  public EntityChangeListeners(final Instance<EntityChangeListener<?>> listeners) {
    this.entityClassVsListeners =
        new LazyLoader<>(
            () -> {
              final Map<Class<?>, List<EntityChangeListener<?>>> entityClassVsListeners =
                  new HashMap<>();
              for (final EntityChangeListener<?> listener : listeners) {
                if (!(listener instanceof GlobalEntityChangeListener)) {
                  entityClassVsListeners
                      .computeIfAbsent(listener.entityClass(), _ -> new ArrayList<>())
                      .add(listener);
                }
              }
              return Map.copyOf(entityClassVsListeners);
            });
    this.globalListeners =
        new LazyLoader<>(
            () -> {
              final List<GlobalEntityChangeListener> globalListeners = new ArrayList<>();
              for (final EntityChangeListener<?> listener : listeners) {
                if (listener instanceof GlobalEntityChangeListener globalEntityChangeListener) {
                  globalListeners.add(globalEntityChangeListener);
                }
              }
              return List.copyOf(globalListeners);
            });
  }

  /**
   * Tells every listener to {@code entityClass} (and all global listeners) about {@code change}.
   */
  @SuppressWarnings("unchecked")
  public <T extends BaseEntity> void publish(
      final Class<T> entityClass, final EntityChange<T> change) {
      final List<EntityChangeListener<?>> listeners =
              CollectionUtils.nullSafeList(entityClassVsListeners.get().getOrDefault(entityClass, List.of()));
      listeners.addAll(CollectionUtils.nullSafeList(globalListeners.get()));
    for (final EntityChangeListener<?> listener : listeners) {
      try {
        // Listeners are kept by the entity class they listen to, so this one takes T.
        ((EntityChangeListener<T>) listener).onChange(entityClass, change);
      } catch (final RuntimeException exception) {
        LOG.error(
            "Listener {} failed on {} of {}",
            listener.getClass().getName(),
            change,
            entityClass.getSimpleName(),
            exception);
      }
    }
  }

  public boolean hasListeners(final Class<? extends BaseEntity> entityClass) {
    return entityClassVsListeners.get().containsKey(entityClass);
  }
}
