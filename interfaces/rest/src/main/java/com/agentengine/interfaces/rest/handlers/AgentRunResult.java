package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Optional;

public record AgentRunResult(String finalAnswer, String thoughts) {

  public static AgentRunResult fromEvents(final List<Event> events) {
    if (events == null || events.isEmpty()) {
      return new AgentRunResult(null, null);
    }
    String finalAnswer = null;
    StringBuilder thoughtsBuilder = null;
    for (final Event event : events) {
      final Content content = event == null ? null : event.content().orElse(null);
      if (content == null) {
        continue;
      }
      for (final Part part : content.parts().orElse(List.of())) {
        final Optional<String> text = part.text();
        if (text.isEmpty()) {
          continue;
        }
        final String value = text.get();
        if (StringUtils.isBlank(value)) {
          continue;
        }
        if (part.thought().orElse(false)) {
          if (thoughtsBuilder == null) {
            thoughtsBuilder = new StringBuilder();
          } else if (thoughtsBuilder.length() > 0) {
            thoughtsBuilder.append("\n");
          }
          thoughtsBuilder.append(value);
        } else {
          finalAnswer = value;
        }
      }
    }
    final String thoughts = thoughtsBuilder == null ? null : thoughtsBuilder.toString();
    return new AgentRunResult(finalAnswer, thoughts);
  }

}
