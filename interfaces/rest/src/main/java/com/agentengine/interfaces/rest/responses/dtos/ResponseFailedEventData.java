package com.agentengine.interfaces.rest.responses.dtos;

import java.util.Map;
import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Event data for response.failed event
 */
public class ResponseFailedEventData extends BaseEventData {
  private final Map<String, Object> response;

  public ResponseFailedEventData(Map<String, Object> response) {
    super("response.failed");
    this.response = response;
  }

  public Map<String, Object> getResponse() {
    return response;
  }
}