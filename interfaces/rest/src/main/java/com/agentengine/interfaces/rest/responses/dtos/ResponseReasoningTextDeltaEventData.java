package com.agentengine.interfaces.rest.responses.dtos;

import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Event data for response.reasoning_text.delta event
 */
public class ResponseReasoningTextDeltaEventData extends BaseEventData {
  private final String delta;
  @JSONField(name = "content_index")
  private final Integer contentIndex;

  public ResponseReasoningTextDeltaEventData(String delta, int contentIndex) {
    super("response.reasoning_text.delta");
    this.delta = delta;
    this.contentIndex = contentIndex;
  }

  public String getDelta() {
    return delta;
  }

  public Integer getContentIndex() {
    return contentIndex;
  }
}