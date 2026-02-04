package com.agentengine.interfaces.rest.responses.dtos;

import java.util.HashMap;
import java.util.Map;

/**
 * Event data for response.output_item.done event
 */
public class ToolCallResultEventData extends OutputItemAddedEventData {

  public ToolCallResultEventData(String id, String output, int index) {
    super(Map.of(
            "type", "function_call_output",
            "call_id", id,
            "output", output
    ), index);
  }
}