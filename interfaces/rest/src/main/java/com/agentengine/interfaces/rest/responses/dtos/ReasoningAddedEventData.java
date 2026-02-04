package com.agentengine.interfaces.rest.responses.dtos;

import java.util.List;
import java.util.Map;

public class ReasoningAddedEventData extends OutputItemAddedEventData {

  public ReasoningAddedEventData(int outputIndex) {
    super(Map.of("type", "reasoning", "summary", List.of()), outputIndex);
  }
}
