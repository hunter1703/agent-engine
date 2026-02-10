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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

public class ToolAwareSessionService implements BaseSessionService {
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
    final Event decoratedEvent = decorateToolEvent(event);
    return delegate.appendEvent(session, decoratedEvent).map(ignored -> event);
  }

  private static Event decorateToolEvent(final Event event) {
    final Content content = event.content().orElse(null);
    if (content == null) {
      return event;
    }
    final List<Part> parts = content.parts().orElse(List.of());
    if (parts.isEmpty()) {
      return event;
    }
    if (parts.getFirst().text().filter(StringUtils::isNotBlank).isPresent()) {
      return event;
    }
    final String toolText = buildToolText(parts);
    if (StringUtils.isBlank(toolText)) {
      return event;
    }
    final List<Part> newParts = new ArrayList<>(parts.size() + 1);
    newParts.add(Part.builder().text(toolText).build());
    newParts.addAll(parts);
    final Content updatedContent = content.toBuilder().parts(newParts).build();
    return event.toBuilder().content(Optional.of(updatedContent)).build();
  }

  private static String buildToolText(final List<Part> parts) {
    final StringBuilder builder = new StringBuilder();
    for (final Part part : parts) {
      part.functionCall().ifPresent(call -> builder.append(formatToolCall(call)).append('\n'));
      part.functionResponse().ifPresent(response -> {
        builder.append(formatToolResponse(response)).append('\n');
      });
    }
    final String text = builder.toString().trim();
    return text.isEmpty() ? null : text;
  }

  private static String formatToolCall(final FunctionCall call) {
    final String name = call.name().orElse("tool");
    final String args = JsonUtils.toJson(call.args().orElse(Map.of()));
    return "Tool call: " + name + " " + args;
  }

  private static String formatToolResponse(final FunctionResponse response) {
    final String name = response.name().orElse("tool");
    final String payload = JsonUtils.toJson(response.response().orElse(Map.of()));
    return "Tool result: " + name + " " + payload;
  }
}
