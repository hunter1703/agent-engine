package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.PekkoSerializable;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public record BroadcasterState(Deque<SequencedEvent<?>> retainedEvents) implements PekkoSerializable {

    public static BroadcasterState empty() {
        return new BroadcasterState(new LinkedList<>());
    }

    public BroadcasterState withPublished(final SequencedEvent<?> event, final int maxRetainedEvents) {
        final LinkedList<SequencedEvent<?>> next = new LinkedList<>(retainedEvents);
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

    public List<SequencedEvent<?>> eventsAfter(final Long lastSeenSequence) {
        if (lastSeenSequence == null || retainedEvents.isEmpty()) {
            return List.of();
        }

        final ArrayList<SequencedEvent<?>> result = new ArrayList<>();
        for (final SequencedEvent<?> event : retainedEvents) {
            if (event.sequence() > lastSeenSequence) {
                result.add(event);
            }
        }
        return result;
    }
}
