package com.agentengine.interfaces.rest.responses.dtos;

import java.util.Map;

public class ToolCallAddedEventData extends OutputItemAddedEventData {

  public ToolCallAddedEventData(String id, String name, int outputIndex) {
    super(Map.of("type", "function_call", "id", id, "name", name, "arguments", ""), outputIndex);
  }
}
