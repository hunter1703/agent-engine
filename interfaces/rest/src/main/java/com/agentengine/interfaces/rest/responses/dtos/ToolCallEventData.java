package com.agentengine.interfaces.rest.responses.dtos;

import java.util.Map;

public class ToolCallEventData extends OutputItemDoneEventData {

  public ToolCallEventData(String id, String name, String args, int index) {
    super(Map.of("type", "function_call", "id", id, "name", name, "arguments", args), index);
  }
}