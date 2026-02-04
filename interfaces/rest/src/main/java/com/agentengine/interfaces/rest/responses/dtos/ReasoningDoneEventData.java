package com.agentengine.interfaces.rest.responses.dtos;

import java.util.List;
import java.util.Map;

public class ReasoningDoneEventData extends OutputItemDoneEventData {

  public ReasoningDoneEventData(String summary, int outputIndex) {
    super(Map.of("type", "reasoning", "summary", buildSummaryItems(summary)), outputIndex);
  }

  private static List<Map<String, Object>> buildSummaryItems(String summary) {
    if (summary == null || summary.isBlank()) {
      return List.of();
    }
    return List.of(Map.of("type", "summary_text", "text", summary));
  }
}
