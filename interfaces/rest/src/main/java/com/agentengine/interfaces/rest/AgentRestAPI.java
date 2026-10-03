package com.agentengine.interfaces.rest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.util.agents.AgentFileDetails;
import com.agentengine.util.agents.beans.AgentSchedule;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.codec.JsonCodec;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.codec.SimpleJsonCodec;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.FlowableUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.context.ContextAware;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import com.agentengine.util.tenancy.PermissionedCache;
import com.agui.community.core.agent.Context;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.CustomEvent;
import com.agui.community.core.event.Event;
import com.agui.community.core.message.Message;
import io.reactivex.rxjava3.core.Flowable;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/v1/agent")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Agent", description = "Agent Management APIs")
@ContextAware
public class AgentRestAPI {

  private final AgentService agentService;
  private final PermissionedCache<BaseAgentConfig> agentCache;
  private final RuntimeService runtimeService;
  private final JsonCodec jsonCodec;

  @Inject
  public AgentRestAPI(
      final MicroServiceClientProvider microServiceClientProvider,
      final PermissionedCache<BaseAgentConfig> agentCache,
      final SimpleJsonCodec jsonCodec) {
    this.agentService = microServiceClientProvider.getRaw(AgentService.class);
    this.agentCache = agentCache;
    this.runtimeService = microServiceClientProvider.getRaw(RuntimeService.class);
    this.jsonCodec = jsonCodec;
  }

  @POST
  @Path("/")
  @Operation(summary = "Create an agent")
  @APIResponse(
      responseCode = "201",
      description = "Agent created",
      content = @Content(schema = @Schema(implementation = BaseAgentConfig.class)))
  @APIResponse(responseCode = "409", description = "Agent already exists")
  @RunOnVirtualThread
  public BaseAgentConfig createAgent(final BaseAgentConfig agentConfig) {
    if (agentConfig == null) {
      throw new IllegalArgumentException("Agent config is required");
    }
    return agentService.createAgent(agentConfig);
  }

  @POST
  @Path("/upsert")
  @Operation(summary = "Upsert an agent")
  @APIResponse(
      responseCode = "200",
      description = "Agent created or updated",
      content = @Content(schema = @Schema(implementation = BaseAgentConfig.class)))
  @RunOnVirtualThread
  public BaseAgentConfig upsertAgent(final BaseAgentConfig agentConfig) {
    if (agentConfig == null) {
      throw new WebApplicationException("Agent config is required", 400);
    }
    if (StringUtils.isBlank(agentConfig.getId())) {
      throw new IllegalArgumentException("Agent ID is required");
    }
    return agentService.saveAgent(agentConfig);
  }

  @PUT
  @Path("/{agentId}")
  @Operation(summary = "Update an agent")
  @APIResponse(
      responseCode = "200",
      description = "Agent updated",
      content = @Content(schema = @Schema(implementation = BaseAgentConfig.class)))
  @APIResponse(responseCode = "400", description = "Path agentId must match payload id")
  @APIResponse(responseCode = "404", description = "Agent not found")
  @RunOnVirtualThread
  public BaseAgentConfig updateAgent(
      @PathParam("agentId") final String agentId, final BaseAgentConfig agentConfig) {
    if (agentConfig == null) {
      throw new IllegalArgumentException("Agent config is required");
    }
    if (StringUtils.isBlank(agentId)) {
      throw new IllegalArgumentException("Agent ID is required");
    }
    if (StringUtils.isNotBlank(agentConfig.getId()) && !agentId.equals(agentConfig.getId())) {
      throw new IllegalArgumentException("Path agentId must match payload id");
    }
    return agentService.updateAgent(agentId, agentConfig);
  }

  @DELETE
  @Path("/{agentId}")
  @Operation(summary = "Delete an agent")
  @APIResponse(responseCode = "204", description = "Agent deleted")
  @APIResponse(responseCode = "404", description = "Agent not found")
  @RunOnVirtualThread
  public void deleteAgent(@PathParam("agentId") final String agentId) {
    if (StringUtils.isBlank(agentId)) {
      throw new IllegalArgumentException("Agent ID is required");
    }
    final boolean deleted = agentService.deleteAgent(agentId);
    if (!deleted) {
      throw new AssetNotFoundException(AssetClass.AGENT, agentId);
    }
  }

  @POST
  @Path("/{agentId}/invoke")
  @Operation(summary = "Invoke an agent and stream AG-UI events")
  @APIResponse(
      responseCode = "200",
      description = "SSE stream of AG-UI events",
      content =
          @Content(mediaType = SERVER_SENT_EVENTS, schema = @Schema(implementation = Event.class)))
  @APIResponse(responseCode = "400", description = "Invalid request parameters")
  @APIResponse(responseCode = "404", description = "Agent not found")
  @Produces(SERVER_SENT_EVENTS)
  @RestStreamElementType(APPLICATION_JSON)
  public Uni<Void> invoke(
      @NotBlank @PathParam("agentId") final String agentId,
      @Valid final RunAgentInput request,
      @jakarta.ws.rs.core.Context final HttpServerResponse response) {
    if (agentCache.get(agentId) == null) {
      throw new AssetNotFoundException(AssetClass.AGENT, agentId);
    }

    final String threadId = StringUtils.isBlank(request.threadId()) ? null : request.threadId();
    final Flowable<?> events =
        FlowableUtils.withScheduled(
            Flowable.fromPublisher(
                runtimeService.startSessionAgui(agentId, threadId, extractUserMessage(request))),
            TimeUnit.SECONDS.toMillis(15),
            () -> new CustomEvent("keep_alive", Map.of("timestamp", System.currentTimeMillis())));
    return RestUtils.writeBatchedSSE(response, events, jsonCodec);
  }

  @POST
  @Path("/{agentId}/schedule")
  @Operation(summary = "Schedule a recurring invocation of an agent")
  @APIResponse(
      responseCode = "201",
      description = "Schedule created",
      content = @Content(schema = @Schema(implementation = AgentSchedule.class)))
  @APIResponse(responseCode = "400", description = "Invalid request parameters")
  @APIResponse(responseCode = "404", description = "Agent not found")
  @RunOnVirtualThread
  public Response schedule(
      @NotBlank @PathParam("agentId") final String agentId, @Valid final AgentSchedule request) {
    request.setAgentId(agentId);
    final AgentSchedule schedule = runtimeService.saveSchedule(request);
    return Response.status(Response.Status.CREATED).entity(schedule).build();
  }

  @PUT
  @Path("/{agentId}/schedule/{scheduleId}")
  @Operation(summary = "Replace a scheduled invocation of an agent")
  @APIResponse(
      responseCode = "200",
      description = "Schedule replaced",
      content = @Content(schema = @Schema(implementation = AgentSchedule.class)))
  @APIResponse(responseCode = "400", description = "Invalid request parameters")
  @APIResponse(responseCode = "404", description = "Agent or schedule not found")
  @APIResponse(
      responseCode = "409",
      description = "The schedule was changed since the version the request names")
  @RunOnVirtualThread
  public AgentSchedule updateSchedule(
      @NotBlank @PathParam("agentId") final String agentId,
      @NotBlank @PathParam("scheduleId") final String scheduleId,
      @Valid final AgentSchedule request) {
    if (runtimeService.getSchedule(scheduleId) == null) {
      throw new AssetNotFoundException(AssetClass.AGENT_SCHEDULE, scheduleId);
    }
    request.setId(scheduleId);
    request.setAgentId(agentId);
    return runtimeService.saveSchedule(request);
  }

  @DELETE
  @Path("/schedule/{scheduleId}")
  @Operation(summary = "Cancel a scheduled invocation of an agent")
  @APIResponse(responseCode = "204", description = "Schedule cancelled")
  @APIResponse(responseCode = "404", description = "Schedule not found")
  @RunOnVirtualThread
  public void cancelSchedule(@NotBlank @PathParam("scheduleId") final String scheduleId) {
    if (!runtimeService.deleteSchedule(scheduleId)) {
      throw new AssetNotFoundException(AssetClass.AGENT_SCHEDULE, scheduleId);
    }
  }

  private static UserMessage extractUserMessage(final RunAgentInput request) {
    final List<Message> messages = request.messages();
    final List<MessagePart> parts = new ArrayList<>();
    final List<AgentFileDetails> knowledgeFiles = new ArrayList<>();
    for (final Context context : CollectionUtils.nullSafeList(request.context())) {
      knowledgeFiles.add(
          new AgentFileDetails(JsonUtils.fromJson(context.value(), FileDetails.class)));
    }
    for (final Message msg : CollectionUtils.nullSafeList(messages)) {
      if (msg instanceof com.agui.community.core.message.UserMessage aguiUserMessage) {
        parts.add(new MessagePart.TextPart(aguiUserMessage.content()));
      }
    }
    if (CollectionUtils.isNotEmpty(parts)) {
      return new UserMessage(parts, knowledgeFiles);
    }
    throw new WebApplicationException("No user message found in messages array", 400);
  }
}
