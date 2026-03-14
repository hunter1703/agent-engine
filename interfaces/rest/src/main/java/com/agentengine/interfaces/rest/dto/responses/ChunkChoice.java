package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A choice in a streaming chunk.
 */
public record ChunkChoice(
    int index,
    MessageDelta delta,
    @JsonProperty("finish_reason") String finishReason,
    Logprobs logprobs
) {
}
