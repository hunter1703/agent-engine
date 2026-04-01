package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Output tokens details (alias for CompletionTokensDetails for backwards compatibility). */
public record OutputTokensDetails(
        @JsonProperty("reasoning_tokens") Integer reasoningTokens,
        @JsonProperty("audio_tokens") Integer audioTokens) {}
