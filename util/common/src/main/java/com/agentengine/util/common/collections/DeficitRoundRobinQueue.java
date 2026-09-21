package com.agentengine.util.common.collections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

public final class DeficitRoundRobinQueue<K, V> {

  private final Map<K, QueueContext<V>> queueMap = new HashMap<>();
  private final List<K> activeQueueKeys = new ArrayList<>();
  private final ToIntFunction<V> weigher;
  private int cursor = 0;

  public DeficitRoundRobinQueue(final ToIntFunction<V> weigher) {
    this.weigher = weigher;
  }

  public void enqueue(
      final K key, final V value, final int quantum, final Supplier<Queue<V>> queueFactory) {
    final QueueContext<V> ctx =
        queueMap.computeIfAbsent(key, _ -> new QueueContext<>(queueFactory.get(), quantum));
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
        ctx.deficitCounter = 0;
        ctx.visitedThisTurn = false;
        activeQueueKeys.remove(cursor);
        continue;
      }

      if (!ctx.visitedThisTurn) {
        ctx.deficitCounter += ctx.quantum;
        ctx.visitedThisTurn = true;
      }

      final V head = ctx.queue.peek();
      final int size = weigher.applyAsInt(head);

      if (ctx.deficitCounter >= size) {
        ctx.queue.poll();
        ctx.deficitCounter -= size;
        return head;
      } else {
        ctx.visitedThisTurn = false;
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
    private final int quantum;
    private int deficitCounter;
    private boolean visitedThisTurn;

    private QueueContext(final Queue<V> queue, final int quantum) {
      this.queue = queue;
      this.quantum = quantum;
      this.deficitCounter = 0;
      this.visitedThisTurn = false;
    }
  }
}
