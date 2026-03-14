package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Responses API request.
 */
public record ResponsesRequest(String model, String input, List<Message> messages, Boolean stream, String user,
    @JsonProperty("max_output_tokens") Integer maxOutputTokens, Double temperature, @JsonProperty("top_p") Double topP, List<Tool> tools,
    @JsonProperty("tool_choice") Object toolChoice, @JsonProperty("parallel_tool_calls") Boolean parallelToolCalls,
    @JsonProperty("previous_response_id") String previousResponseId, Reasoning reasoning,
    @JsonProperty("response_format") ResponseFormat responseFormat, Boolean store, String instructions, Object truncation,
    Map<String, Object> metadata) {
  public ResponsesRequest {
    if (stream == null)
      stream = false;
    if (parallelToolCalls == null)
      parallelToolCalls = true;
    if (store == null)
      store = true;
  }
}
