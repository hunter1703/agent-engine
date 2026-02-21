package com.agentengine.interfaces.rest.responses.dtos;

import java.util.HashMap;
import java.util.Map;

public class CreatedEventData extends BaseResponsesEventData {
  private final Map<String, Object> response;

  public CreatedEventData(final String id, final String model) {
    super("response.created");
    final Map<String, Object> responseData = new HashMap<>();
    responseData.put("id", id);
    responseData.put("model", model);
    responseData.put("status", "in_progress");
    responseData.put("object", "response");
    responseData.put("created", System.currentTimeMillis() / 1000);
    this.response = responseData;
  }

  public Map<String, Object> getResponse() {
    return response;
  }
}
