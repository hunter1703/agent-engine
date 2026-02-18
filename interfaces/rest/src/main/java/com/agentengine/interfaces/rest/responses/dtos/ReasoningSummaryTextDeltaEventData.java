package com.agentengine.interfaces.rest.responses.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReasoningSummaryTextDeltaEventData extends BaseResponsesEventData {
  private final String delta;
  @JsonProperty("summary_index")
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