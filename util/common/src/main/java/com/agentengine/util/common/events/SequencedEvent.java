package com.agentengine.util.common.events;

public record SequencedEvent<E>(long sequence, E payload) {}
