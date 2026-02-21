package com.agentengine.interfaces.rest.responses.dtos;

import java.util.Map;

public class CompletedEventData extends BaseResponsesEventData {
  private final Map<String, Object> response;

  public CompletedEventData(String id) {
    super("response.completed");
    this.response = Map.of("id", id);
  }

  public Map<String, Object> getResponse() {
    return response;
  }
}
