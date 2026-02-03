package com.agentengine.interfaces.rest.responses.dtos;

import java.util.Map;
import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Event data for response.done event
 */
public class ResponseDoneEventData extends BaseEventData {
  private final Map<String, Object> response;

  public ResponseDoneEventData(Map<String, Object> response) {
    super("response.done");
    this.response = response;
  }

  public Map<String, Object> getResponse() {
    return response;
  }
}