package com.agentengine.interfaces.rest.responses.dtos;

import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Event data for response.output_text.delta event
 */
public class ResponseOutputTextDeltaEventData extends BaseEventData {
  private final String delta;
  @JSONField(name = "output_index")
  private final Integer outputIndex;

  public ResponseOutputTextDeltaEventData(String delta, int outputIndex) {
    super("response.output_text.delta");
    this.delta = delta;
    this.outputIndex = outputIndex;
  }

  public String getDelta() {
    return delta;
  }

  public Integer getOutputIndex() {
    return outputIndex;
  }
}