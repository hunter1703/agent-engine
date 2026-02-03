package com.agentengine.interfaces.rest.responses.dtos;

import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Event data for response.reasoning_summary_part.added event
 */
public class ResponseReasoningSummaryPartAddedEventData extends BaseEventData {
  @JSONField(name = "summary_index")
  private final Integer summaryIndex;

  public ResponseReasoningSummaryPartAddedEventData(int summaryIndex) {
    super("response.reasoning_summary_part.added");
    this.summaryIndex = summaryIndex;
  }

  public Integer getSummaryIndex() {
    return summaryIndex;
  }
}