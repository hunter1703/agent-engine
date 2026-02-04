package com.agentengine.interfaces.rest.responses.dtos;

import java.util.HashMap;
import java.util.Map;

/**
 * Event data for response.in_progress event
 */
public class InProgressEventData extends BaseResponsesEventData {
  private final Map<String, Object> response;

  public InProgressEventData() {
    super("response.in_progress");
    Map<String, Object> data = new HashMap<>();
    data.put("status", "in_progress");
    this.response = data;
  }

  public Map<String, Object> getResponse() {
    return response;
  }
}