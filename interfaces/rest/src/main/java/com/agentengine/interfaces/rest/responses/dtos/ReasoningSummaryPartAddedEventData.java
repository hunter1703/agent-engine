package com.agentengine.interfaces.rest.responses.dtos;

import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Event data for response.reasoning_summary_part.added event
 */
public class ReasoningSummaryPartAddedEventData extends BaseResponsesEventData {
  @JSONField(name = "summary_index")
  private final Integer summaryIndex;

  public ReasoningSummaryPartAddedEventData(int thinkingIndex) {
    super("response.reasoning_summary_part.added");
    this.summaryIndex = thinkingIndex;
  }

  public Integer getSummaryIndex() {
    return summaryIndex;
  }
}