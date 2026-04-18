package com.agentengine.interfaces.rest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;

import com.agentengine.core.api.services.SessionService;
import com.agentengine.interfaces.rest.dto.ConfirmSessionRequest;
import com.agentengine.runtime.api.services.RuntimeService;
import com.agentengine.util.agents.agui.AGUIEventMapper;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.reactivestreams.Publisher;

@Path("/v1/session")
@Tag(name = "Session Stream", description = "Session event stream and confirmation APIs")
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
            content = @Content(mediaType = SERVER_SENT_EVENTS, schema = @Schema(implementation = BaseEvent.class)))
    @APIResponse(responseCode = "404", description = "Session not found")
    public Publisher<BaseEvent> stream(@NotBlank @PathParam("sessionId") final String sessionId) {
        final AgentSession session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new AssetNotFoundException(AssetClass.AGENT_SESSION, sessionId);
        }
        final AGUIEventMapper mapper =
                new AGUIEventMapper(session.getRootSessionId(), session.getRootAgentId(), AGUIEventMapper.Mode.REPLAY);
        return Flowable.fromPublisher(runtimeService.subscribeToSession(sessionId))
                .concatMap(mapper::map);
    }

    @POST
    @Path("/{sessionId}/confirm/{confirmationId}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RunOnVirtualThread
    @Operation(summary = "Confirm a paused session")
    @APIResponse(
            responseCode = "200",
            description = "Confirmation acknowledgement",
            content =
                    @Content(
                            mediaType = APPLICATION_JSON,
                            schema = @Schema(implementation = ConfirmSessionResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid confirmation payload")
    @APIResponse(responseCode = "404", description = "Session not found")
    public ConfirmSessionResponse confirm(
            @NotBlank @PathParam("sessionId") final String sessionId,
            @NotBlank @PathParam("confirmationId") final String confirmationId,
            @Valid @NotNull final ConfirmSessionRequest confirmRequest) {
        runtimeService.confirmSession(
                sessionId,
                new Confirmation(
                        confirmationId, confirmRequest.getConfirmed(), Map.of("answer", confirmRequest.getMessage())));
        return new ConfirmSessionResponse(true);
    }

    public record ConfirmSessionResponse(boolean confirmed) {}
}
