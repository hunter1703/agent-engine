package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SessionUtils {
  public static final String TITLE_KEY = "session:title";
  public static final String CREATED_AT_KEY = "session:createdAt";
  public static final String TITLE_UPDATED_AT_KEY = "session:titleUpdatedAt";
  public static final String TITLE_EVENT_COUNT_KEY = "session:titleEventCount";
  public static final String EVENT_COUNT_KEY = "session:eventCount";

  private SessionUtils() {
  }

  public static List<Event> filterConversationEvents(final List<Event> events) {
    if (events == null || events.isEmpty()) {
      return List.of();
    }
    final List<Event> filtered = new ArrayList<>();
    for (final Event event : events) {
      if (hasConversationContent(event)) {
        filtered.add(event);
      }
    }
    return filtered;
  }

  public static boolean hasConversationContent(final Event event) {
    if (event == null) {
      return false;
    }
    return event.content().flatMap(Content::parts).map(parts -> !parts.isEmpty()).orElse(false);
  }

  public static String buildTranscript(final List<Event> events) {
    if (events == null || events.isEmpty()) {
      return "";
    }
    final StringBuilder builder = new StringBuilder();
    for (final Event event : events) {
      final String text = extractText(event);
      if (StringUtils.isBlank(text)) {
        continue;
      }
      final String author = StringUtils.isBlank(event.author()) ? "assistant" : event.author();
      builder.append(author).append(": ").append(text.trim()).append("\n");
    }
    return builder.toString().trim();
  }

  private static String extractText(final Event event) {
    if (event == null) {
      return null;
    }
    final Content content = event.content().orElse(null);
    if (content == null) {
      return null;
    }
    final String text = content.text();
    if (StringUtils.isNotBlank(text)) {
      return text;
    }
    return summarizeParts(content.parts().orElse(List.of()));
  }

  private static String summarizeParts(final List<Part> parts) {
    if (parts == null || parts.isEmpty()) {
      return null;
    }
    final StringBuilder builder = new StringBuilder();
    for (final Part part : parts) {
      part.functionCall()
          .ifPresent(call -> builder.append("Tool call: ").append(call.name().orElse("tool")).append('\n'));
      part.functionResponse()
          .ifPresent(response -> builder.append("Tool result: ").append(response.name().orElse("tool")).append('\n'));
    }
    final String summary = builder.toString().trim();
    return summary.isEmpty() ? null : summary;
  }

  public static ConcurrentMap<String, Object> buildInitialState() {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    state.put(CREATED_AT_KEY, Instant.now().toEpochMilli());
    state.put(TITLE_EVENT_COUNT_KEY, 0L);
    state.put(EVENT_COUNT_KEY, 0L);
    state.put(TITLE_UPDATED_AT_KEY, 0L);
    return state;
  }
}
