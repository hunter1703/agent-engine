package com.agentengine.api;

import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.AgentListener;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.ToolCall;
import com.agentengine.engine.utils.StringUtils;
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
  public InvokeResponse invoke(final InvokeRequest request) {
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

  @GET
  @Path("/events")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  public Multi<AgentEvent> events(
      @QueryParam("agentName") final String agentName,
      @QueryParam("agentConfigPath") final String agentConfigPath,
      @QueryParam("sessionId") final String sessionId) {
    AgentEngine engine = agentService.getOrStartEngine(agentName, agentConfigPath);
    String resolvedSessionId = getOrCreateSession(sessionId);
    return Multi.createFrom()
        .emitter(
            emitter -> {
              AtomicBoolean active = new AtomicBoolean(true);
              emitter.emit(new AgentEvent("session", resolvedSessionId, Map.of("status", "ready")));
              engine.registerListener(
                  new AgentListener() {
                    @Override
                    public void onToolPlan(final String sessionId, final List<ToolCall> toolCalls) {
                      if (!active.get()) {
                        return;
                      }
                      List<ToolCallDto> payload =
                          toolCalls.stream()
                              .map(call -> new ToolCallDto(call.id(), call.name(), call.args()))
                              .toList();
                      emitter.emit(new AgentEvent("tool_plan", sessionId, payload));
                    }

                    @Override
                    public void onToolExecution(
                        final String sessionId,
                        final com.agentengine.engine.beans.ToolExecution toolExecution) {
                      if (!active.get()) {
                        return;
                      }
                      ToolExecutionDto payload =
                          new ToolExecutionDto(
                              toolExecution.getId(),
                              toolExecution.getToolCall().name(),
                              toolExecution.getStatus(),
                              toolExecution.getOutput(),
                              toolExecution.getDurationMs());
                      emitter.emit(new AgentEvent("tool_execution", sessionId, payload));
                    }

                    @Override
                    public void onReasoningStart(final String sessionId) {
                      if (!active.get()) {
                        return;
                      }
                      emitter.emit(
                          new AgentEvent("reasoning_start", sessionId, Map.of("status", "start")));
                    }

                    @Override
                    public void onReasoningEnd(final String sessionId) {
                      if (!active.get()) {
                        return;
                      }
                      emitter.emit(
                          new AgentEvent("reasoning_end", sessionId, Map.of("status", "end")));
                    }

                    @Override
                    public void onToolRepair(final String sessionId) {
                      if (!active.get()) {
                        return;
                      }
                      emitter.emit(
                          new AgentEvent("tool_repair", sessionId, Map.of("status", "repair")));
                    }
                  });

              emitter.onTermination(() -> active.set(false));
            });
  }

  private static String getOrCreateSession(final String sessionId) {
    if (StringUtils.isNotBlank(sessionId)) {
      return sessionId;
    }
    return UUID.randomUUID().toString();
  }
}
