package com.agentengine.interfaces.rest.responses.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReasoningSummaryPartAddedEventData extends BaseResponsesEventData {
  @JsonProperty("summary_index")
  private final Integer summaryIndex;

  public ReasoningSummaryPartAddedEventData(int thinkingIndex) {
    super("response.reasoning_summary_part.added");
    this.summaryIndex = thinkingIndex;
  }

  public Integer getSummaryIndex() {
    return summaryIndex;
  }
}
