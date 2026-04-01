package com.agentengine.util.common.events;

/**
 * Monotonic ordering envelope
 *
 * @param sequence increasing sequence number
 * @param payload event payload
 * @param <E> payload type
 */
public record SequencedEvent<E>(long sequence, E payload) {}
