package com.agentengine.interfaces.rest.responses.dtos;

import java.util.Map;
import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Event data for response.in_progress event
 */
public class ResponseInProgressEventData extends BaseEventData {
  private final Map<String, Object> response;

  public ResponseInProgressEventData(Map<String, Object> response) {
    super("response.in_progress");
    this.response = response;
  }

  public Map<String, Object> getResponse() {
    return response;
  }
}