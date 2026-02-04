package com.agentengine.interfaces.rest.responses.dtos;

import java.util.Map;

/**
 * Event data for response.done event
 */
public class DoneEventData extends BaseResponsesEventData {
  private final Map<String, Object> response;

  public DoneEventData(Map<String, Object> response) {
    super("response.done");
    this.response = response;
  }

  public Map<String, Object> getResponse() {
    return response;
  }
}