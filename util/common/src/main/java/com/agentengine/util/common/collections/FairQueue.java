package com.agentengine.util.common.collections;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
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
 */
public final class FairQueue<K, V> {

  private final Map<K, QueueContext<V>> queueMap = new HashMap<>();
  private final List<K> activeQueueKeys = new ArrayList<>();
  private final ToIntFunction<V> weigher;
  private final ToIntFunction<K> tokensPerRound;
  private final Function<K, Queue<V>> queueFactory;
  private int cursor = 0;

  public FairQueue() {
    this(_ -> new ArrayDeque<>());
  }

  public FairQueue(final Function<K, Queue<V>> queueFactory) {
    this(_ -> 1, _ -> 1, queueFactory);
  }

  public FairQueue(
      final ToIntFunction<V> weigher,
      final ToIntFunction<K> tokensPerRound,
      final Function<K, Queue<V>> queueFactory) {
    this.weigher = weigher;
    this.tokensPerRound = tokensPerRound;
    this.queueFactory = queueFactory;
  }

  public void enqueue(final K key, final V value) {
    final QueueContext<V> ctx =
        queueMap.computeIfAbsent(
            key, k -> new QueueContext<>(queueFactory.apply(k), tokensPerRound.applyAsInt(k)));
    final boolean wasEmpty = ctx.queue.isEmpty();
    ctx.queue.offer(value);
    if (wasEmpty) {
      activeQueueKeys.add(key);
    }
  }

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
        return head;
      } else {
        ctx.tokensGranted = false;
        cursor++;
      }
    }
    return null;
  }

  public boolean isEmpty() {
    return activeQueueKeys.isEmpty();
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
