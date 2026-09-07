package com.agentengine.util.common.events;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Monotonic ordering envelope. */
public record SequencedEvent<E>(
    long sequence,
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.CLASS,
            include = JsonTypeInfo.As.PROPERTY,
            property = "@class")
        E payload) {}
