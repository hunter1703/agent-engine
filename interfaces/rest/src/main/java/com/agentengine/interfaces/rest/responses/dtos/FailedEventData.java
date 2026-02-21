package com.agentengine.interfaces.rest.responses.dtos;

import java.util.HashMap;
import java.util.Map;

public class FailedEventData extends BaseResponsesEventData {
  private final Map<String, Object> response;

  public FailedEventData(String id, String error) {
    super("response.failed");

    Map<String, Object> errorData = new HashMap<>();
    errorData.put("type", "error");
    errorData.put("code", "internal_error");
    errorData.put("message", error);

    Map<String, Object> responseData = new HashMap<>();
    responseData.put("id", id);
    responseData.put("object", "response");
    responseData.put("created_at", System.currentTimeMillis() / 1000);
    responseData.put("status", "failed");
    responseData.put("background", false);
    responseData.put("error", errorData);

    this.response = responseData;
  }

  public Map<String, Object> getResponse() {
    return response;
  }
}
