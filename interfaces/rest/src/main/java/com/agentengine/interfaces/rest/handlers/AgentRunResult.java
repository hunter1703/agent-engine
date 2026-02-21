package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public record AgentRunResult(String finalAnswer, String thoughts) {

  public static AgentRunResult fromEvents(final List<Event> events) {
    if (events == null || events.isEmpty()) {
      return new AgentRunResult(null, null);
    }
    String finalAnswer = null;
    String thoughts = null;
    for (final Event event : events) {
      final Content content = event == null ? null : event.content().orElse(null);
      if (content == null) {
        continue;
      }
      final String text = content.text();
      if (StringUtils.isNotBlank(text)) {
        finalAnswer = text;
      }
      final String thoughtText =
          content.parts().orElse(List.of()).stream()
              .filter(part -> part.thought().orElse(false))
              .map(Part::text)
              .flatMap(Optional::stream)
              .filter(StringUtils::isNotBlank)
              .collect(Collectors.joining("\n"));
      if (StringUtils.isNotBlank(thoughtText)) {
        thoughts = thoughtText;
      }
    }
    return new AgentRunResult(finalAnswer, thoughts);
  }
}
