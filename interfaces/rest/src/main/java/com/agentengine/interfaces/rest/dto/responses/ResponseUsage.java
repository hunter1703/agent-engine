package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response usage statistics.
 */
public record ResponseUsage(
    @JsonProperty("input_tokens") int inputTokens,
    @JsonProperty("output_tokens") int outputTokens,
    @JsonProperty("total_tokens") int totalTokens,
    @JsonProperty("input_tokens_details") InputTokensDetails inputTokensDetails,
    @JsonProperty("output_tokens_details") OutputTokensDetails outputTokensDetails
) {
}
