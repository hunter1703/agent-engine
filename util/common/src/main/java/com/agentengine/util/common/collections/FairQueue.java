package com.agentengine.util.common.collections;

import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * A fair queue that implements the Deficit Round Robin (DRR) algorithm.
 *
 * <p>Items are partitioned by key, with each key assigned a token quota per round. In each
 * round-robin pass over the active keys, a key is allowed to emit items as long as its accumulated
 * token balance covers the item weight. Unused tokens carry over to the next round, preventing
 * starvation while maintaining fairness across tenants.
 *
 * <p>Also a {@link Queue}: given a {@code keyExtractor}, {@link #offer} derives the key from the
 * value itself, so one {@code FairQueue} can serve as the per-key inner queue of another — e.g. a
 * queue fair across tenants whose own per-tenant queues are, in turn, fair across tags.
 *
 * <p>Each key's own queue is capacity-restricted by {@code maxSizePerKey}: once a key's queue is at
 * that limit, {@link #enqueue} and {@link #offer} drop the value and return {@code false}, matching
 * {@link Queue#offer}'s documented contract for a bounded queue. Nesting a {@code FairQueue} as
 * another's per-key inner queue composes two independent limits — the outer bounds how much one key
 * can hold in total, the inner bounds how much one of ITS keys can hold — so one hot key one level
 * down can't consume the whole budget of the key that owns it.
 */
public final class FairQueue<K, V> extends AbstractQueue<V> {

  private final Map<K, QueueContext<V>> queueMap = new HashMap<>();
  private final List<K> activeQueueKeys = new ArrayList<>();
  private final ToIntFunction<V> weigher;
  private final ToIntFunction<K> tokensPerRound;
  private final Function<K, Queue<V>> queueFactory;
  private final Function<V, K> keyExtractor;
  private final ToIntFunction<K> maxSizePerKey;
  private int cursor = 0;
  private int size = 0;

  public FairQueue() {
    this(_ -> new ArrayDeque<>());
  }

  public FairQueue(final Function<K, Queue<V>> queueFactory) {
    this(null, _ -> 1, _ -> 1, queueFactory, _ -> Integer.MAX_VALUE);
  }

  public FairQueue(final Function<V, K> keyExtractor, final Function<K, Queue<V>> queueFactory) {
    this(keyExtractor, _ -> 1, _ -> 1, queueFactory, _ -> Integer.MAX_VALUE);
  }

  public FairQueue(
      final Function<V, K> keyExtractor,
      final ToIntFunction<V> weigher,
      final ToIntFunction<K> tokensPerRound,
      final Function<K, Queue<V>> queueFactory) {
    this(keyExtractor, weigher, tokensPerRound, queueFactory, _ -> Integer.MAX_VALUE);
  }

  public FairQueue(
      final Function<V, K> keyExtractor,
      final ToIntFunction<V> weigher,
      final ToIntFunction<K> tokensPerRound,
      final Function<K, Queue<V>> queueFactory,
      final ToIntFunction<K> maxSizePerKey) {
    this.keyExtractor = keyExtractor;
    this.weigher = weigher;
    this.tokensPerRound = tokensPerRound;
    this.queueFactory = queueFactory;
    this.maxSizePerKey = maxSizePerKey;
  }

  public boolean enqueue(final K key, final V value) {
    final QueueContext<V> ctx =
        queueMap.computeIfAbsent(
            key, k -> new QueueContext<>(queueFactory.apply(k), tokensPerRound.applyAsInt(k)));
    if (ctx.queue.size() >= maxSizePerKey.applyAsInt(key)) {
      return false;
    }
    final boolean wasEmpty = ctx.queue.isEmpty();
    ctx.queue.offer(value);
    if (wasEmpty) {
      activeQueueKeys.add(key);
    }
    size++;
    return true;
  }

  @Override
  public boolean offer(final V value) {
    if (keyExtractor == null) {
      throw new UnsupportedOperationException(
          "This FairQueue has no keyExtractor; call enqueue(key, value) instead of offer(value)");
    }
    return enqueue(keyExtractor.apply(value), value);
  }

  @Override
  public V poll() {
    while (!activeQueueKeys.isEmpty()) {
      if (cursor >= activeQueueKeys.size()) {
        cursor = 0;
      }

      final K currentKey = activeQueueKeys.get(cursor);
      final QueueContext<V> ctx = queueMap.get(currentKey);

      if (ctx.queue.isEmpty()) {
        ctx.tokenBalance = 0;
        ctx.tokensGranted = false;
        activeQueueKeys.remove(cursor);
        continue;
      }

      if (!ctx.tokensGranted) {
        ctx.tokenBalance += ctx.tokens;
        ctx.tokensGranted = true;
      }

      final V head = ctx.queue.peek();
      final int size = weigher.applyAsInt(head);

      if (ctx.tokenBalance >= size) {
        ctx.queue.poll();
        ctx.tokenBalance -= size;
        this.size--;
        return head;
      } else {
        ctx.tokensGranted = false;
        cursor++;
      }
    }
    return null;
  }

  /**
   * The item {@link #poll} would return next, without granting tokens, advancing the round, or
   * dropping drained keys — those only happen as a side effect of an actual {@code poll}.
   */
  @Override
  public V peek() {
    final int keyCount = activeQueueKeys.size();
    for (int offset = 0, localCursor = cursor; offset < keyCount; offset++) {
      if (localCursor >= keyCount) {
        localCursor = 0;
      }
      final QueueContext<V> ctx = queueMap.get(activeQueueKeys.get(localCursor));
      if (!ctx.queue.isEmpty()) {
        final V head = ctx.queue.peek();
        final int available = ctx.tokenBalance + (ctx.tokensGranted ? 0 : ctx.tokens);
        if (available >= weigher.applyAsInt(head)) {
          return head;
        }
      }
      localCursor++;
    }
    return null;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public Iterator<V> iterator() {
    final List<V> all = new ArrayList<>();
    for (final QueueContext<V> ctx : queueMap.values()) {
      all.addAll(ctx.queue);
    }
    return all.iterator();
  }

  private static final class QueueContext<V> {
    private final Queue<V> queue;
    private final int tokens;
    private int tokenBalance;
    private boolean tokensGranted;

    private QueueContext(final Queue<V> queue, final int tokens) {
      this.queue = queue;
      this.tokens = tokens;
      this.tokenBalance = 0;
      this.tokensGranted = false;
    }
  }
}
