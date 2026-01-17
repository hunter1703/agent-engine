package com.agentengine.api;

import com.agentengine.client.AgentRequest;
import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.events.AgentEvent;
import com.agentengine.engine.events.AgentEventAdapter;
import com.agentengine.engine.events.AgentEventPublisher;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.utils.StringUtils;
import com.agentengine.interfaces.AgentService;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.resteasy.reactive.RestStreamElementType;

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
  @Blocking
  @RunOnVirtualThread
  public InvokeResponse invoke(final AgentRequest request) {
    AgentEngine engine =
        agentService.getOrStartEngine(request.getAgentName(), request.getAgentConfigPath());
    String sessionId = getOrCreateSession(request.getSessionId());
    Message response = engine.invoke(sessionId, Message.user(request.getMessage()));
    return new InvokeResponse(
        sessionId,
        response == null ? null : response.getContent(),
        response == null ? null : response.getThoughts());
  }

  @POST
  @Path("/prompt")
  @Blocking
  @RunOnVirtualThread
  public PromptResponse buildPrompt(final AgentRequest request) {
    AgentEngine engine =
        agentService.getOrStartEngine(request.getAgentName(), request.getAgentConfigPath());
    String sessionId = getOrCreateSession(request.getSessionId());
    List<MessageDto> messages =
        engine.buildPrompt(sessionId).stream()
            .map(
                message ->
                    new MessageDto(message.getRole().name().toLowerCase(), message.getContent()))
            .toList();
    return new PromptResponse(sessionId, messages);
  }

  @POST
  @Path("/events")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  @Blocking
  @RunOnVirtualThread
  public Multi<AgentEvent> events(final AgentRequest agentRequest) {
    AgentEngine engine =
        agentService.getOrStartEngine(
            agentRequest.getAgentName(), agentRequest.getAgentConfigPath());
    final String sessionId = getOrCreateSession(agentRequest.getSessionId());
    return Multi.createFrom()
        .emitter(
            emitter -> {
              AtomicBoolean active = new AtomicBoolean(true);
              emitter.emit(new AgentEvent("session", sessionId, Map.of("status", "ready")));
              AgentEventPublisher publisher =
                  event -> {
                    if (active.get()) {
                      emitter.emit(event);
                    }
                  };
              engine.registerListener(sessionId, new AgentEventAdapter(publisher));
              engine.invoke(sessionId, Message.user(agentRequest.getMessage()));
              emitter.onTermination(
                  () -> {
                    active.set(false);
                    engine.unRegisterListener(sessionId);
                  });
            });
  }

  private static String getOrCreateSession(final String sessionId) {
    if (StringUtils.isNotBlank(sessionId)) {
      return sessionId;
    }
    return UUID.randomUUID().toString();
  }
}
