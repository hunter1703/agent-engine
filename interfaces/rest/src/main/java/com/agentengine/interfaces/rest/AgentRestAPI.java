package com.agentengine.interfaces.rest;

import com.agentengine.core.api.services.AgentService;
import com.agentengine.core.api.services.SessionService;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.AssetNotFoundException;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/v1/agent")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Agent", description = "Agent Management APIs")
@RunOnVirtualThread
public class AgentRestAPI {
    private final AgentService agentService;
    private final SessionService sessionService;

    @Inject
    public AgentRestAPI(final AgentService agentService, final SessionService sessionService) {
        this.agentService = agentService;
        this.sessionService = sessionService;
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
}
