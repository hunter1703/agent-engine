package com.agentengine.interfaces.rest.responses.dtos;

import com.alibaba.fastjson2.annotation.JSONField;

public class OutputTextDeltaEventData extends BaseResponsesEventData {
  private final String delta;
  @JSONField(name = "output_index")
  private final Integer outputIndex;

  public OutputTextDeltaEventData(String delta, int outputIndex) {
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