package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Chat completion response.
 */
public record ChatCompletionResponse(String id, String object, long created, String model, List<Choice> choices, CompletionUsage usage,
    @JsonProperty("system_fingerprint") String systemFingerprint, @JsonProperty("service_tier") String serviceTier, String provider) {
  public ChatCompletionResponse {
    if (object == null)
      object = "chat.completion";
  }
}
