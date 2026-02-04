package com.agentengine.interfaces.rest.responses.dtos;

import java.util.List;
import java.util.Map;

/**
 * Event data for response.output_item.done event
 */
public class MessageCompletedEventData extends OutputItemDoneEventData {

  public MessageCompletedEventData(String message, int outputIndex) {
    super(Map.of("type", "message", "role", "assistant", "content",
        List.of(Map.of("type", "output_text", "text", message))), outputIndex);
  }
}