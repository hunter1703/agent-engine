package com.agentengine.scheduler.core.actor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.ToIntFunction;

/**
 * Caps how many concurrent items of each key may be in flight.
 *
 * <p>A permit reserves every one of its keys or none of them, so an item held up by one exhausted
 * key does not consume capacity in the others while it waits. Reservations carry the time they were
 * made and are evicted once older than the TTL, which bounds the damage from a completion message
 * that never arrives — the slot frees itself rather than leaking permanently.
 *
 * <p>Thread-safe: the node-scoped instance is shared by every runner entity on the pod, and {@link
 * #tryAcquire} is all-or-nothing across keys, which no concurrent map can make atomic on its own.
 * Contention is negligible — one acquire and one release per dispatch.
 */
public final class ConcurrencyLimiter {

  private final ToIntFunction<String> capacityByKey;
  private final long permitTtlMillis;
  private final Map<String, Map<String, Long>> keyVsPermits = new HashMap<>();

  public ConcurrencyLimiter(final ToIntFunction<String> capacityByKey, final long permitTtlMillis) {
    this.capacityByKey = capacityByKey;
    this.permitTtlMillis = permitTtlMillis;
  }

  /** Reserves a slot in every key, or nothing at all if any key is exhausted. */
  public synchronized boolean tryAcquire(
      final List<String> keys, final String permitId, final long now) {
    final List<String> acquired = new ArrayList<>(keys.size());
    for (final String key : keys) {
      if (!acquireOne(key, permitId, now)) {
        release(acquired, permitId);
        return false;
      }
      acquired.add(key);
    }
    return true;
  }

  public synchronized void release(final List<String> keys, final String permitId) {
    for (final String key : keys) {
      final Map<String, Long> permits = keyVsPermits.get(key);
      if (permits == null) {
        continue;
      }
      permits.remove(permitId);
      if (permits.isEmpty()) {
        keyVsPermits.remove(key);
      }
    }
  }

  public synchronized int evictExpired(final long now) {
    int evicted = 0;
    final Iterator<Entry<String, Map<String, Long>>> keys = keyVsPermits.entrySet().iterator();
    while (keys.hasNext()) {
      final Map<String, Long> permits = keys.next().getValue();
      final Iterator<Entry<String, Long>> entries = permits.entrySet().iterator();
      while (entries.hasNext()) {
        if (now - entries.next().getValue() > permitTtlMillis) {
          entries.remove();
          evicted++;
        }
      }
      if (permits.isEmpty()) {
        keys.remove();
      }
    }
    return evicted;
  }

  public synchronized int inFlight(final String key) {
    final Map<String, Long> permits = keyVsPermits.get(key);
    return permits == null ? 0 : permits.size();
  }

  private boolean acquireOne(final String key, final String permitId, final long now) {
    final Map<String, Long> permits = keyVsPermits.computeIfAbsent(key, k -> new HashMap<>());
    if (!permits.containsKey(permitId) && permits.size() >= capacityByKey.applyAsInt(key)) {
      return false;
    }
    permits.put(permitId, now);
    return true;
  }
}
