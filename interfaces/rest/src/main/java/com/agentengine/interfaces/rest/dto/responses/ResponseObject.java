package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Responses API response object. */
public record ResponseObject(
    String id,
    String object,
    @JsonProperty("created_at") long createdAt,
    String model,
    List<OutputItem> output,
    @JsonProperty("parallel_tool_calls") Boolean parallelToolCalls,
    @JsonProperty("previous_response_id") String previousResponseId,
    Reasoning reasoning,
    Boolean store,
    Double temperature,
    @JsonProperty("top_p") Double topP,
    @JsonProperty("tool_choice") Object toolChoice,
    List<Tool> tools,
    ResponseUsage usage,
    @JsonProperty("service_tier") String serviceTier,
    @JsonProperty("system_fingerprint") String systemFingerprint,
    String status,
    @JsonProperty("completed_at") Long completedAt,
    ResponseError error,
    @JsonProperty("incomplete_details") IncompleteDetails incompleteDetails,
    Object instructions,
    @JsonProperty("max_output_tokens") Integer maxOutputTokens,
    TextConfig text,
    String truncation,
    String user,
    Map<String, Object> metadata) {
  public ResponseObject {
    if (object == null) object = "response";
    if (parallelToolCalls == null) parallelToolCalls = true;
    if (store == null) store = true;
  }
}
