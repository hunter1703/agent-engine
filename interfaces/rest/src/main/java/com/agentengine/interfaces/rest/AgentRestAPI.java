package com.agentengine.interfaces.rest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.interfaces.rest.dto.InvokeAgentRequest;
import com.agentengine.util.agents.agui.AGUIEventMapper;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agui.core.types.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.reactivestreams.Publisher;

@Path("/v1/agent")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Agent", description = "Agent Management APIs")
@RunOnVirtualThread
public class AgentRestAPI {
    private final AgentService agentService;
    private final SessionService sessionService;
    private final RuntimeService runtimeService;

    @Inject
    public AgentRestAPI(
            final AgentService agentService, final SessionService sessionService, final RuntimeService runtimeService) {
        this.agentService = agentService;
        this.sessionService = sessionService;
        this.runtimeService = runtimeService;
    }

    @POST
    @Path("/")
    @Operation(summary = "Create an agent")
    @APIResponse(
            responseCode = "201",
            description = "Agent created",
            content = @Content(schema = @Schema(implementation = BaseAgentConfig.class)))
    @APIResponse(responseCode = "409", description = "Agent already exists")
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
    public BaseAgentConfig updateAgent(@PathParam("agentId") final String agentId, final BaseAgentConfig agentConfig) {
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
    public void deleteAgent(@PathParam("agentId") final String agentId) {
        if (StringUtils.isBlank(agentId)) {
            throw new IllegalArgumentException("Agent ID is required");
        }
        final boolean deleted = agentService.deleteAgent(agentId);
        if (!deleted) {
            throw new AssetNotFoundException(AssetClass.AGENT, agentId);
        }
    }

    @DELETE
    @Path("/session/{sessionId}")
    @Operation(summary = "Delete a session")
    @APIResponse(responseCode = "204", description = "Session deleted")
    @APIResponse(responseCode = "404", description = "Session not found")
    public void deleteSession(@PathParam("sessionId") final String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            throw new IllegalArgumentException("Session ID is required");
        }
        sessionService.deleteSession(sessionId);
    }

    @POST
    @Path("/session/{sessionId}/rollback")
    @Operation(summary = "Roll back a session to before the given run")
    @APIResponse(responseCode = "204", description = "Rollback applied")
    @APIResponse(responseCode = "400", description = "runId is required")
    @APIResponse(responseCode = "404", description = "Session not found")
    @APIResponse(responseCode = "409", description = "Session is currently running")
    public void rollbackSession(
            @NotBlank @PathParam("sessionId") final String sessionId,
            @NotBlank @QueryParam("runId") final String runId) {
        if (sessionService.getSession(sessionId) == null) {
            throw new AssetNotFoundException(AssetClass.AGENT_SESSION, sessionId);
        }
        runtimeService.rollbackSession(sessionId, runId);
    }

    @POST
    @Path("/{agentId}/invoke")
    @Operation(summary = "Invoke an agent and stream AG-UI events")
    @APIResponse(
            responseCode = "200",
            description = "SSE stream of AG-UI events",
            content = @Content(mediaType = SERVER_SENT_EVENTS, schema = @Schema(implementation = BaseEvent.class)))
    @APIResponse(responseCode = "400", description = "Invalid request parameters")
    @APIResponse(responseCode = "404", description = "Agent not found")
    @Produces(SERVER_SENT_EVENTS)
    @RestStreamElementType(APPLICATION_JSON)
    public Publisher<BaseEvent> invoke(
            @NotBlank @PathParam("agentId") final String agentId, @Valid final InvokeAgentRequest request) {
        if (agentService.getAgent(agentId) == null) {
            throw new AssetNotFoundException(AssetClass.AGENT, agentId);
        }

        final AtomicReference<AGUIEventMapper> mapper = new AtomicReference<>();
        return Flowable.fromPublisher(
                        runtimeService.startSession(agentId, request.getThreadId(), extractUserMessage(request)))
                .doOnNext(event -> {
                    if (mapper.get() == null) {
                        mapper.set(new AGUIEventMapper(event.getSessionId(), agentId, AGUIEventMapper.Mode.LIVE));
                    }
                })
                .concatMap(event -> mapper.get().map(event));
    }

    private static UserMessage extractUserMessage(final InvokeAgentRequest request) {
        final List<InvokeAgentRequest.AguiMessage> msgs = request.getMessages();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            final InvokeAgentRequest.AguiMessage msg = msgs.get(i);
            if ("user".equalsIgnoreCase(msg.getRole())) {
                return convertAguiMessage(msg);
            }
        }
        throw new WebApplicationException("No user message found in messages array", 400);
    }

    @SuppressWarnings("unchecked")
    private static UserMessage convertAguiMessage(final InvokeAgentRequest.AguiMessage msg) {
        final Object content = msg.getContent();

        if (content instanceof String text) {
            return UserMessage.ofText(text);
        }

        if (content instanceof List<?> parts) {
            final List<MessagePart> messageParts = new ArrayList<>();
            for (final Object raw : parts) {
                if (!(raw instanceof Map<?, ?> part)) {
                    continue;
                }
                final String type = CollectionUtils.getStringValueFromMap(part, "type");

                if ("text".equals(type)) {
                    final String text = CollectionUtils.getStringValueFromMap(part, "text");
                    if (StringUtils.isNotBlank(text)) {
                        messageParts.add(new MessagePart.TextPart(text));
                    }
                } else if ("document".equals(type) || "image".equals(type)) {
                    final FileDetails fd = extractFileDetails(part);
                    if (fd != null) {
                        messageParts.add(new MessagePart.FilePart(fd));
                    }
                }
            }
            if (!messageParts.isEmpty()) {
                return new UserMessage(messageParts);
            }
        }

        throw new WebApplicationException("Unable to parse message content", 400);
    }

    @SuppressWarnings("unchecked")
    private static FileDetails extractFileDetails(final Map<String, Object> part) {
        final Map<String, Object> source = CollectionUtils.getValueFromMap(part, "source");
        final String sourceType = CollectionUtils.getStringValueFromMap(source, "type");
        final String value = CollectionUtils.getStringValueFromMap(source, "value");
        final String mimeType = CollectionUtils.getStringValueFromMap(source, "mimeType");

        if (StringUtils.isBlank(value)) {
            return null;
        }

        final String name = value.substring(value.lastIndexOf('/') + 1);
        final FileDetails.StorageType storageType =
                "url".equalsIgnoreCase(sourceType) ? FileDetails.StorageType.URL : FileDetails.StorageType.UNKNOWN;

        return new FileDetails(name, value, storageType, mimeType, -1L);
    }
}
