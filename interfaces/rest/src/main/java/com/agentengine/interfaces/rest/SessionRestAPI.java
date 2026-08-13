package com.agentengine.interfaces.rest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;

import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.util.agents.agui.AGUIEventMapper;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agui.community.core.event.Event;
import com.agui.community.core.interrupt.Resume;
import io.reactivex.rxjava3.core.Flowable;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.reactivestreams.Publisher;

@Path("/v1/session")
@Tag(name = "Session Stream", description = "Session event stream and resume APIs")
public class SessionRestAPI {

    private final RuntimeService runtimeService;
    private final SessionService sessionService;

    @Inject
    public SessionRestAPI(final RuntimeService runtimeService, final SessionService sessionService) {
        this.runtimeService = runtimeService;
        this.sessionService = sessionService;
    }

    @GET
    @Path("/{sessionId}/stream")
    @Produces(SERVER_SENT_EVENTS)
    @RestStreamElementType(APPLICATION_JSON)
    @Operation(summary = "Subscribe to a session event stream")
    @APIResponse(
            responseCode = "200",
            description = "SSE stream of AG-UI events: committed history, uncommitted turn events, then live events",
            content = @Content(mediaType = SERVER_SENT_EVENTS, schema = @Schema(implementation = Event.class)))
    @APIResponse(responseCode = "404", description = "Session not found")
    public Publisher<Event> stream(
            @NotBlank @PathParam("sessionId") final String sessionId, @QueryParam("liveOnly") boolean liveOnly) {
        final AgentSession session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new AssetNotFoundException(AssetClass.AGENT_SESSION, sessionId);
        }
        final AGUIEventMapper mapper = new AGUIEventMapper(
                session.getRootSessionId(),
                session.getRootAgentId(),
                liveOnly ? AGUIEventMapper.Mode.LIVE : AGUIEventMapper.Mode.REPLAY);
        return Flowable.fromPublisher(runtimeService.subscribeToSession(sessionId, liveOnly))
                .concatMap(mapper::map);
    }

    @POST
    @Path("/{sessionId}/resume")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RunOnVirtualThread
    @Operation(summary = "Resume a paused session")
    @APIResponse(responseCode = "400", description = "Invalid resume payload")
    public Response resume(@NotBlank @PathParam("sessionId") final String sessionId, Resume resume) {
        //noinspection unchecked
        final Map<String, Object> payload = (Map<String, Object>) resume.payload();
        final Boolean accepted = CollectionUtils.getBooleanValueFromMap(payload, "accepted");
        runtimeService.resumeSession(
                sessionId,
                new ResumeRequest(
                        resume.interruptId(),
                        accepted,
                        Map.of("answer", CollectionUtils.getStringValueFromMap(payload, "answer", ""))));
        return Response.accepted().build();
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
}
