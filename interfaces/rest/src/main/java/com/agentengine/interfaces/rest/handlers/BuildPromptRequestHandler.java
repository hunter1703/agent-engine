package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.dto.ContentDto;
import com.agentengine.interfaces.rest.dto.PromptResponse;
import com.agentengine.interfaces.rest.services.AgentRuntimeManager;
import com.agentengine.interfaces.rest.services.AgentRuntime;
import com.google.adk.events.Event;
import com.google.adk.sessions.ListEventsResponse;
import com.google.genai.types.Content;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Objects;

@Singleton
public class BuildPromptRequestHandler extends AbstractAgentRequestHandler<AgentResponse> {

  public BuildPromptRequestHandler(final AgentRuntimeManager agentManager) {
    super(agentManager);
  }

  @Override
  public RequestType requestType() {
    return RequestType.BUILD_PROMPT;
  }

  @Override
  public AgentResponse handle(final AgentRequest request) {
    final AgentRuntime runtime = getOrCreateRuntime(request);
    final String sessionId = ensureSession(runtime, request.getSessionId());
    final ListEventsResponse response = runtime.sessionService()
        .listEvents(runtime.appName(), AgentRuntimeManager.DEFAULT_USER_ID, sessionId).blockingGet();
    final List<ContentDto> contents = response.events().stream().map(BuildPromptRequestHandler::toContentDto)
        .filter(Objects::nonNull).toList();
    return new PromptResponse(sessionId, contents);
  }

  private static ContentDto toContentDto(final Event event) {
    if (event == null) {
      return null;
    }
    final String content = event.content().map(Content::text).orElse(null);
    if (StringUtils.isBlank(content)) {
      return null;
    }
    return new ContentDto(roleFor(event.author()), content);
  }

  private static String roleFor(final String author) {
    if (author == null) {
      return "assistant";
    }
    return "user".equalsIgnoreCase(author) ? "user" : "assistant";
  }
}
