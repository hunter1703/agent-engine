package com.agentengine.interfaces.rest.responses.dtos;

import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Event data for response.reasoning_summary_text.delta event
 */
public class ReasoningSummaryTextDeltaEventData extends BaseResponsesEventData {
  private final String delta;
  @JSONField(name = "summary_index")
  private final Integer summaryIndex;

  public ReasoningSummaryTextDeltaEventData(String delta, int thinkingIndex) {
    super("response.reasoning_summary_text.delta");
    this.delta = delta;
    this.summaryIndex = thinkingIndex;
  }

  public String getDelta() {
    return delta;
  }

  public Integer getSummaryIndex() {
    return summaryIndex;
  }
}