package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Usage statistics. */
public record CompletionUsage(
    @JsonProperty("prompt_tokens") int promptTokens,
    @JsonProperty("completion_tokens") int completionTokens,
    @JsonProperty("total_tokens") int totalTokens,
    @JsonProperty("prompt_tokens_details") PromptTokensDetails promptTokensDetails,
    @JsonProperty("completion_tokens_details") CompletionTokensDetails completionTokensDetails) {
  public CompletionUsage(int promptTokens, int completionTokens, int totalTokens) {
    this(promptTokens, completionTokens, totalTokens, null, null);
  }
}
