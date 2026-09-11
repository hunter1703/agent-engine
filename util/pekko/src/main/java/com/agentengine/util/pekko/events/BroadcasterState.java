package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.Copyable;
import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.PekkoSerializable;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public record BroadcasterState(Deque<SequencedEvent<Copyable<?>>> retainedEvents)
    implements PekkoSerializable {

  public static BroadcasterState empty() {
    return new BroadcasterState(new LinkedList<>());
  }

  public BroadcasterState withPublished(
      final SequencedEvent<Copyable<?>> event, final int maxRetainedEvents) {
    final LinkedList<SequencedEvent<Copyable<?>>> next = new LinkedList<>(retainedEvents);
    next.add(event);
    if (next.size() > maxRetainedEvents) {
      next.pollFirst();
    }
    return new BroadcasterState(next);
  }

  public boolean canReplayFrom(final Long lastSeenSequence) {
    if (lastSeenSequence == null) {
      return false; // new subscription — no replay needed or requested
    }
    return lastSeenSequence >= oldestRetainedSequence() - 1L;
  }

  public long oldestRetainedSequence() {
    if (retainedEvents.isEmpty()) {
      return 0;
    }
    return retainedEvents.peek().sequence();
  }

  public long latestPublishedSequence() {
    return retainedEvents.isEmpty() ? 0 : retainedEvents.peekLast().sequence();
  }

  public List<SequencedEvent<Copyable<?>>> eventsAfter(final Long lastSeenSequence) {
    if (lastSeenSequence == null || retainedEvents.isEmpty()) {
      return List.of();
    }

    final ArrayList<SequencedEvent<Copyable<?>>> result = new ArrayList<>();
    for (final SequencedEvent<Copyable<?>> event : retainedEvents) {
      if (event.sequence() > lastSeenSequence) {
        result.add(event);
      }
    }
    return result;
  }
}
