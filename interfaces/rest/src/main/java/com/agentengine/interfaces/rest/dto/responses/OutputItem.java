package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OutputItem(
    String type,
    String id,
    String status,
    String role,
    List<OutputContent> content,
    @JsonProperty("call_id") String callId,
    String name,
    String arguments,
    String output,
    @JsonProperty("reasoning_summary") List<ReasoningSummary> reasoningSummary) {}
