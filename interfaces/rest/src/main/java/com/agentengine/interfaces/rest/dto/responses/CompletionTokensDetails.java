package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CompletionTokensDetails(
    @JsonProperty("reasoning_tokens") Integer reasoningTokens,
    @JsonProperty("audio_tokens") Integer audioTokens,
    @JsonProperty("accepted_prediction_tokens") Integer acceptedPredictionTokens,
    @JsonProperty("rejected_prediction_tokens") Integer rejectedPredictionTokens) {}
