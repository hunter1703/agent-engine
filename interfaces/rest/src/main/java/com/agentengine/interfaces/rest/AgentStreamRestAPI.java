package com.agentengine.interfaces.rest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;

import com.agentengine.core.api.services.AgentExecutionService;
import com.agentengine.core.api.services.AgentService;
import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.interfaces.rest.dto.ConfirmSessionRequest;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agui.core.event.BaseEvent;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.reactivestreams.Publisher;

@Path("/v1/agent")
@Consumes(APPLICATION_JSON)
@Produces(SERVER_SENT_EVENTS)
@RestStreamElementType(APPLICATION_JSON)
@Tag(name = "Agent Stream", description = "Agent Execution APIs")
public class AgentStreamRestAPI {

    private final AgentService agentService;
    private final AgentExecutionService agentExecutionService;

    @Inject
    public AgentStreamRestAPI(final AgentService agentService, final AgentExecutionService agentExecutionService) {
        this.agentService = agentService;
        this.agentExecutionService = agentExecutionService;
    }

    @POST
    @Path("/events")
    @Operation(summary = "Stream agent events")
    @APIResponse(
            responseCode = "200",
            description = "SSE event stream in AG-UI format",
            content = @Content(mediaType = SERVER_SENT_EVENTS, schema = @Schema(implementation = BaseEvent.class)))
    @APIResponse(responseCode = "400", description = "Invalid request parameters")
    @APIResponse(responseCode = "404", description = "Agent not found")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Publisher<BaseEvent> events(@Valid final AgentRequest request) {
        if (agentService.getAgent(request.getAgentId()) == null) {
            throw new AssetNotFoundException(AssetClass.AGENT, request.getAgentId());
        }
        return agentExecutionService.run(request.getAgentId(), request.getSessionId(), request.getMessage());
    }

    @POST
    @Path("/session/confirm/events")
    @Operation(summary = "Confirm a paused session and stream resumed events")
    @APIResponse(
            responseCode = "200",
            description = "SSE event stream in AG-UI format",
            content = @Content(mediaType = SERVER_SENT_EVENTS, schema = @Schema(implementation = BaseEvent.class)))
    @APIResponse(responseCode = "400", description = "Invalid confirmation payload")
    @APIResponse(responseCode = "404", description = "Session not found")
    @APIResponse(responseCode = "408", description = "Pending confirmation timed out")
    public Publisher<BaseEvent> confirmEvents(@Valid @NotNull final ConfirmSessionRequest confirmRequest) {
        return agentExecutionService.confirmSession(
                confirmRequest.getSessionId(),
                confirmRequest.getConfirmationId(),
                confirmRequest.getConfirmed(),
                confirmRequest.getMessage());
    }
}
