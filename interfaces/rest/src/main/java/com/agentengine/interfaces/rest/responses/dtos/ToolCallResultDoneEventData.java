package com.agentengine.interfaces.rest.responses.dtos;

import java.util.Map;

public class ToolCallResultDoneEventData extends OutputItemDoneEventData {

  public ToolCallResultDoneEventData(String id, String output, int index) {
    super(Map.of("type", "function_call_output", "call_id", id, "output", output), index);
  }
}
