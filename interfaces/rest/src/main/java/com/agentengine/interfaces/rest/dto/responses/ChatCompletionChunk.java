package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Streaming chunk for chat completions.
 */
public record ChatCompletionChunk(
    String id,
    String object,
    long created,
    String model,
    List<ChunkChoice> choices,
    @JsonProperty("system_fingerprint") String systemFingerprint,
    @JsonProperty("service_tier") String serviceTier,
    CompletionUsage usage
) {
  public ChatCompletionChunk {
    if (object == null)
      object = "chat.completion.chunk";
  }
}
