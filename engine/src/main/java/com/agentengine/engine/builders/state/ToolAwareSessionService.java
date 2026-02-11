package com.agentengine.engine.builders.state;

import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.events.Event;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.ListEventsResponse;
import com.google.adk.sessions.ListSessionsResponse;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

public class ToolAwareSessionService implements BaseSessionService {
  private static final Logger LOG = LoggerFactory.getLogger(ToolAwareSessionService.class);
  private final BaseSessionService delegate;

  public ToolAwareSessionService(final BaseSessionService delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
  }

  @Override
  public Single<Session> createSession(final String appName, final String userId,
      final ConcurrentMap<String, Object> state, final String sessionId) {
    return delegate.createSession(appName, userId, state, sessionId);
  }

  @Override
  public Maybe<Session> getSession(final String appName, final String userId, final String sessionId,
      final Optional<GetSessionConfig> config) {
    return delegate.getSession(appName, userId, sessionId, config);
  }

  @Override
  public Single<ListSessionsResponse> listSessions(final String appName, final String userId) {
    return delegate.listSessions(appName, userId);
  }

  @Override
  public Completable deleteSession(final String appName, final String userId, final String sessionId) {
    return delegate.deleteSession(appName, userId, sessionId);
  }

  @Override
  public Single<ListEventsResponse> listEvents(final String appName, final String userId, final String sessionId) {
    return delegate.listEvents(appName, userId, sessionId);
  }

  @Override
  public Completable closeSession(final Session session) {
    return delegate.closeSession(session);
  }

  @Override
  public Single<Event> appendEvent(final Session session, final Event event) {
    LOG.debug("Appending event to session - event_id={}, author={}, content={}", 
              event.id(), event.author(), event.content().map(Content::text).orElse("null"));
    final Event decoratedEvent = decorateToolEvent(event);
    LOG.debug("Decorated event - event_id={}, author={}, content={}", 
              decoratedEvent.id(), decoratedEvent.author(), 
              decoratedEvent.content().map(Content::text).orElse("null"));
    return delegate.appendEvent(session, decoratedEvent).map(ignored -> event);
  }

  private static Event decorateToolEvent(final Event event) {
    LOG.debug("Decorating tool event - event_id={}, author={}", event.id(), event.author());
    final Content content = event.content().orElse(null);
    if (content == null) {
      LOG.debug("Event has no content, returning as-is");
      return event;
    }
    final List<Part> parts = content.parts().orElse(List.of());
    if (parts.isEmpty()) {
      LOG.debug("Event has no parts, returning as-is");
      return event;
    }
    if (parts.getFirst().text().filter(StringUtils::isNotBlank).isPresent()) {
      LOG.debug("First part has text content, returning as-is");
      return event;
    }
    final String toolText = buildToolText(parts);
    LOG.debug("Built tool text: '{}'", toolText);
    if (StringUtils.isBlank(toolText)) {
      LOG.debug("Tool text is blank, returning as-is");
      return event;
    }
    final List<Part> newParts = new ArrayList<>(parts.size() + 1);
    newParts.add(Part.builder().text(toolText).build());
    newParts.addAll(parts);
    final Content updatedContent = content.toBuilder().parts(newParts).build();
    LOG.debug("Returning decorated event with {} parts", newParts.size());
    return event.toBuilder().content(Optional.of(updatedContent)).build();
  }

  private static String buildToolText(final List<Part> parts) {
    LOG.debug("Building tool text from {} parts", parts.size());
    final StringBuilder builder = new StringBuilder();
    for (final Part part : parts) {
      part.functionCall().ifPresent(call -> {
        String formattedCall = formatToolCall(call);
        LOG.debug("Found function call: {}", formattedCall);
        builder.append(formattedCall).append('\n');
      });
      part.functionResponse().ifPresent(response -> {
        String formattedResponse = formatToolResponse(response);
        LOG.debug("Found function response: {}", formattedResponse);
        builder.append(formattedResponse).append('\n');
      });
    }
    final String text = builder.toString().trim();
    LOG.debug("Built tool text: '{}'", text);
    return text.isEmpty() ? null : text;
  }

  private static String formatToolCall(final FunctionCall call) {
    final String name = call.name().orElse("tool");
    final String args = JsonUtils.toJson(call.args().orElse(Map.of()));
    String result = "Tool call: " + name + " " + args;
    LOG.debug("Formatted tool call: {}", result);
    return result;
  }

  private static String formatToolResponse(final FunctionResponse response) {
    final String name = response.name().orElse("tool");
    final String payload = JsonUtils.toJson(response.response().orElse(Map.of()));
    String result = "Tool result: " + name + " " + payload;
    LOG.debug("Formatted tool response: {}", result);
    return result;
  }
}
