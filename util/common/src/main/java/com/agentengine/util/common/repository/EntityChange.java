package com.agentengine.util.common.repository;

import com.agentengine.util.common.beans.BaseEntity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A committed write to a repository's entities: the entities it changed, each as it is after the
 * write, when the write holds them; otherwise their ids; or — for a write by a filter that pins no
 * ids — the filter it applied.
 */
public sealed interface EntityChange<T extends BaseEntity>
    permits EntityChange.Entities, EntityChange.Ids {

  Type type();

  /** A write to these entities, each as it is after the write, by id. */
  record Entities<T extends BaseEntity>(Type type, Map<String, T> idVsEntity)
      implements EntityChange<T> {
    public Entities {
      idVsEntity = Collections.unmodifiableMap(new LinkedHashMap<>(idVsEntity));
    }
  }

  /** A write to some or all of the entities with these ids. */
  record Ids<T extends BaseEntity>(Type type, Set<String> ids) implements EntityChange<T> {
    public Ids {
      ids = Set.copyOf(ids);
    }
  }

  enum Type {
    UNKNOWN,
    CREATED,
    UPDATED,
    DELETED,
    /** Only the entities' access lists changed. */
    ACCESS_CHANGED;

    public static Type valueOfOrDefault(final String value) {
      if (value == null || value.isBlank()) {
        return UNKNOWN;
      }
      try {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (final IllegalArgumentException exception) {
        return UNKNOWN;
      }
    }
  }
}
