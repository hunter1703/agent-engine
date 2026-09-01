package com.agentengine.util.common.events;

/** Monotonic ordering envelope. */
public record SequencedEvent<E>(long sequence, E payload) {}
