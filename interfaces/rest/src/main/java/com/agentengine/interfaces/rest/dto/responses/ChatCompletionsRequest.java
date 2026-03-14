package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Chat completion request.
 */
public record ChatCompletionsRequest(String model, List<Message> messages, Boolean stream, String user,
    @JsonProperty("max_tokens") Integer maxTokens, Double temperature, @JsonProperty("top_p") Double topP, Object stop,
    @JsonProperty("frequency_penalty") Double frequencyPenalty, @JsonProperty("presence_penalty") Double presencePenalty, List<Tool> tools,
    @JsonProperty("tool_choice") Object toolChoice, Boolean logprobs, @JsonProperty("top_logprobs") Integer topLogprobs, Integer n,
    @JsonProperty("stream_options") StreamOptions streamOptions, @JsonProperty("response_format") ResponseFormat responseFormat,
    Integer seed, List<String> modalities, PredictionConfig prediction, AudioConfig audio,
    @JsonProperty("web_search_options") WebSearchOptions webSearchOptions) {
  public ChatCompletionsRequest {
    if (stream == null)
      stream = false;
    if (n == null)
      n = 1;
  }
}
