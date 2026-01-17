package com.agentengine.api;

import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.utils.StringUtils;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/agent")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AgentRestAPI {
  private final AgentService agentService;

  @Inject
  public AgentRestAPI(final AgentService agentService) {
    this.agentService = agentService;
  }

  @POST
  @Path("/invoke")
  public InvokeResponse invoke(final InvokeRequest request) {
    AgentEngine engine = agentService.getOrStartEngine(request.getAgentName(), request.getAgentConfigPath());
    String sessionId = getOrCreateSession(request.getSessionId());
    Message response = engine.invoke(sessionId, Message.user(request.getMessage()));
    return new InvokeResponse(sessionId, response == null ? null : response.getContent(),
        response == null ? null : response.getThoughts());
  }

  @POST
  @Path("/prompt")
  public PromptResponse buildPrompt(final AgentRequest request) {
    AgentEngine engine = agentService.getOrStartEngine(request.getAgentName(), request.getAgentConfigPath());
    String sessionId = getOrCreateSession(request.getSessionId());
    List<MessageDto> messages =
        engine.buildPrompt(sessionId).stream()
            .map(message -> new MessageDto(message.getRole().name().toLowerCase(), message.getContent()))
            .toList();
    return new PromptResponse(sessionId, messages);
  }

  private static String getOrCreateSession(final String sessionId) {
    if (StringUtils.isNotBlank(sessionId)) {
      return sessionId;
    }
    return UUID.randomUUID().toString();
  }
}
