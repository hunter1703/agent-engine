package com.agentengine.interfaces.rest.handlers;

import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.dto.MessageDto;
import com.agentengine.interfaces.rest.dto.PromptResponse;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.interfaces.rest.services.AgentManager;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class BuildPromptRequestHandler extends AbstractAgentRequestHandler<AgentResponse> {

  public BuildPromptRequestHandler(final AgentManager agentManager) {
    super(agentManager);
  }

  @Override
  public RequestType requestType() {
    return RequestType.BUILD_PROMPT;
  }

  @Override
  public AgentResponse handle(final AgentRequest request) {
    final Agent engine = getOrCreateEngine(request);
    final String sessionId = request.getSessionId();
    List<MessageDto> messages = engine.buildPrompt(sessionId).stream()
        .map(message -> new MessageDto(message.getRole().name().toLowerCase(), message.getContent())).toList();
    return new PromptResponse(sessionId, messages);
  }
}
