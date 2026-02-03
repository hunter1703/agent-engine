package com.agentengine.interfaces.rest.responses.dtos;

import java.util.Map;
import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Event data for response.completed event
 */
public class ResponseCompletedEventData extends BaseEventData {
  private final Map<String, Object> response;

  public ResponseCompletedEventData(Map<String, Object> response) {
    super("response.completed");
    this.response = response;
  }

  public Map<String, Object> getResponse() {
    return response;
  }
}