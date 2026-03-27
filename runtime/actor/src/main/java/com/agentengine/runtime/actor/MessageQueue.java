package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.PekkoSerializable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Immutable queue of pending messages for a session. */
public record MessageQueue(List<String> messages) implements PekkoSerializable {

    public static final MessageQueue EMPTY = new MessageQueue(List.of());

    public MessageQueue enqueue(final String message) {
        final var next = new ArrayList<>(messages);
        next.add(message);
        return new MessageQueue(List.copyOf(next));
    }

    public Optional<String> peek() {
        return messages.isEmpty() ? Optional.empty() : Optional.of(messages.getFirst());
    }

    public MessageQueue dequeue() {
        return messages.isEmpty()
                ? this
                : new MessageQueue(List.copyOf(messages.subList(1, messages.size())));
    }

    public boolean isEmpty() { return messages.isEmpty(); }
}
