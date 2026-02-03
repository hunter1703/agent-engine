package com.agentengine.interfaces.rest.responses.dtos;

import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Event data for response.reasoning_summary_text.delta event
 */
public class ResponseReasoningSummaryTextDeltaEventData extends BaseEventData {
  private final String delta;
  @JSONField(name = "summary_index")
  private final Integer summaryIndex;

  public ResponseReasoningSummaryTextDeltaEventData(String delta, int summaryIndex) {
    super("response.reasoning_summary_text.delta");
    this.delta = delta;
    this.summaryIndex = summaryIndex;
  }

  public String getDelta() {
    return delta;
  }

  public Integer getSummaryIndex() {
    return summaryIndex;
  }
}