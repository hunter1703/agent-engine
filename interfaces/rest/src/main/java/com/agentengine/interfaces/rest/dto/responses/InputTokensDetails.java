package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Input tokens details.
 */
public record InputTokensDetails(
    @JsonProperty("cached_tokens") Integer cachedTokens,
    @JsonProperty("audio_tokens") Integer audioTokens,
    @JsonProperty("text_tokens") Integer textTokens
) {
}
