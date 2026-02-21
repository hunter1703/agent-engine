package com.agentengine.interfaces.rest.responses.dtos;

import java.util.List;
import java.util.Map;

public class MessageStartedEventData extends OutputItemAddedEventData {

  public MessageStartedEventData(int outputIndex) {
    super(
        Map.of(
            "type",
            "message",
            "role",
            "assistant",
            "content",
            List.of(Map.of("type", "output_text", "text", ""))),
        outputIndex);
  }
}
