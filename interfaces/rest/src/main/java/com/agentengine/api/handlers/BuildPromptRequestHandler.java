package com.agentengine.api.handlers;

import com.agentengine.api.AgentResponse;
import com.agentengine.api.MessageDto;
import com.agentengine.api.PromptResponse;
import com.agentengine.engine.client.AgentRequest;
import com.agentengine.engine.client.AgentRequest.RequestType;
import com.agentengine.engine.client.AgentEngine;
import com.agentengine.interfaces.AgentManager;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class BuildPromptRequestHandler extends AbstractAgentRequestHandler {

  public BuildPromptRequestHandler(final AgentManager agentManager) {
    super(agentManager);
  }

  @Override
  public RequestType requestType() {
    return RequestType.BUILD_PROMPT;
  }

  @SuppressWarnings("unchecked")
  @Override
  public AgentResponse handle(final AgentRequest request) {
    final AgentEngine engine = getOrCreateEngine(request);
    final String sessionId = request.getSessionId();
    List<MessageDto> messages = engine.buildPrompt(sessionId).stream()
        .map(message -> new MessageDto(message.getRole().name().toLowerCase(), message.getContent())).toList();
    return new PromptResponse(sessionId, messages);
  }
}
