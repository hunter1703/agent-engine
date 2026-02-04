package com.agentengine.interfaces.rest.responses.dtos;

import java.util.Map;
import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Event data for response.output_item.added event
 */
public class OutputItemAddedEventData extends BaseResponsesEventData {
  private final Map<String, Object> item;
  @JSONField(name = "output_index")
  private final Integer outputIndex;

  public OutputItemAddedEventData(Map<String, Object> item, int outputIndex) {
    super("response.output_item.added");
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