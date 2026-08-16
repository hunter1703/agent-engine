package com.agentengine.util.scripts.templated;

/**
 * A raw value (string, map, or list) that can be turned into a {@link Template} which resolves it
 * against a parameters map.
 */
public interface TemplatedType<T> {
  Template<T> build();
}
