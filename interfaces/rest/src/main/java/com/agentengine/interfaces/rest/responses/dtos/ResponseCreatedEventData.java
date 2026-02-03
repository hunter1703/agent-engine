package com.agentengine.interfaces.rest.responses.dtos;

import java.util.Map;
import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Event data for response.created event
 */
public class ResponseCreatedEventData extends BaseEventData {
  private final Map<String, Object> response;

  public ResponseCreatedEventData(Map<String, Object> response) {
    super("response.created");
    this.response = response;
  }

  public Map<String, Object> getResponse() {
    return response;
  }
}