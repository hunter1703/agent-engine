package com.agentengine.util.pekko.actor;

import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of active {@link ChaosMailboxConfig}s, keyed by sharded-entity ID (or
 * {@code "*"} to target every entity of a type). Always present as a CDI singleton — when empty
 * (the default, outside of chaos experiments), {@link MessageFaultInterceptor} degrades to a single
 * map lookup per message and passes everything through.
 *
 * <p>Chaos fault injectors populate this registry; {@code ShardedEntityFactory} implementations
 * consult it via {@link MessageFaultInterceptor} without any build-time dependency on the chaos
 * module.
 */
@Singleton
public final class ChaosMailboxRegistry {

  private static final String WILDCARD_ENTITY_ID = "*";

  private final Map<String, ChaosMailboxConfig> configsByEntityId = new ConcurrentHashMap<>();

  public void register(final String entityId, final ChaosMailboxConfig config) {
    configsByEntityId.put(entityId, config);
  }

  public void remove(final String entityId) {
    configsByEntityId.remove(entityId);
  }

  public Optional<ChaosMailboxConfig> configFor(final String entityId) {
    final ChaosMailboxConfig specific = configsByEntityId.get(entityId);
    return specific != null
        ? Optional.of(specific)
        : Optional.ofNullable(configsByEntityId.get(WILDCARD_ENTITY_ID));
  }

  public static String wildcardEntityId() {
    return WILDCARD_ENTITY_ID;
  }
}
