package com.agentengine.interfaces.rest.responses.dtos;

import java.util.Map;
import com.alibaba.fastjson2.annotation.JSONField;

public class OutputItemDoneEventData extends BaseResponsesEventData {
  private final Map<String, Object> item;
  @JSONField(name = "output_index")
  private final Integer outputIndex;

  public OutputItemDoneEventData(Map<String, Object> item, int outputIndex) {
    super("response.output_item.done");
    this.item = item;
    this.outputIndex = outputIndex;
  }

  public Map<String, Object> getItem() {
    return item;
  }

  public Integer getOutputIndex() {
    return outputIndex;
  }
}