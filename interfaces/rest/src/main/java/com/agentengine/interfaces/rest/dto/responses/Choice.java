package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A choice in a chat completion response.
 */
public record Choice(
    int index,
    Message message,
    @JsonProperty("finish_reason") String finishReason,
    Logprobs logprobs
) {
}
