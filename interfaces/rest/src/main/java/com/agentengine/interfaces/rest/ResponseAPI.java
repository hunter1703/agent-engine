package com.agentengine.interfaces.rest;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.interfaces.rest.dto.responses.*;
import com.agentengine.interfaces.rest.handlers.CompletionsEventMapper;
import com.agentengine.interfaces.rest.handlers.ResponsesEventMapper;
import com.agentengine.interfaces.rest.handlers.ResponsesEventMapper.ResponseOutputEvent;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.query.Sort;
import io.reactivex.rxjava3.core.Flowable;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;
import java.util.UUID;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;

@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "OpenAI Compatible", description = "OpenAI-compatible API endpoints")
@RunOnVirtualThread
public class ResponseAPI {

    private final AgentService agentService;
    private final AgentExecutionService agentExecutionService;

    @Inject
    public ResponseAPI(AgentService agentService, AgentExecutionService agentExecutionService) {
        this.agentService = agentService;
        this.agentExecutionService = agentExecutionService;
    }

    @GET
    @Path("/models")
    @Operation(summary = "List available models")
    @APIResponse(responseCode = "200", description = "List of models")
    public ModelList models() {
        Query query = new Query()
            .withPage(new Page(0, 1000))
            .withSort(new Sort(BaseEntity.FIELD_ID, Sort.Order.ASC));
        PaginatedResult<BaseAgentConfig> agents = agentService.findAgents(query);
        return new ModelList(agents.getItems().stream()
            .map(agent -> new Model(agent.getId(), "model", agent.getCreatedTime() / 1000, "agent-engine"))
            .toList());
    }

    @POST
    @Path("/chat/completions")
    @Produces(SERVER_SENT_EVENTS)
    @RestStreamElementType(APPLICATION_JSON)
    @Operation(summary = "Create chat completion")
    @APIResponse(responseCode = "200", description = "Chat completion response or stream")
    public Flowable<ChatCompletionChunk> chatCompletions(ChatCompletionsRequest request, @Context HttpHeaders headers) {
        if (request == null || request.messages() == null || request.messages().isEmpty()) {
            throw new WebApplicationException("Messages are required", 400);
        }
        String agentId = request.model();
        if (StringUtils.isBlank(agentId)) {
            throw new WebApplicationException("Model is required", 400);
        }

        String prompt = extractUserMessage(request.messages());
        if (StringUtils.isBlank(prompt)) {
            throw new WebApplicationException("User message is required", 400);
        }

        String sessionId = resolveSessionId(agentId, request.user() != null ? request.user() : "anonymous");
        String completionId = "completion-" + UUID.randomUUID();

        return agentExecutionService.run(agentId, sessionId, prompt)
            .map(new CompletionsEventMapper(completionId, agentId)::mapEvent)
            .switchMap(e -> e);
    }

    @POST
    @Path("/responses")
    @Produces(SERVER_SENT_EVENTS)
    @RestStreamElementType(APPLICATION_JSON)
    @Operation(summary = "Create response (Responses API)")
    @APIResponse(responseCode = "200", description = "Response object or stream")
    public Flowable<ResponseOutputEvent> createResponse(ResponsesRequest request, @Context HttpHeaders headers) {
        if (request == null) {
            throw new WebApplicationException("Request body is required", 400);
        }

        String modelId = request.model();
        if (StringUtils.isBlank(modelId)) {
            throw new WebApplicationException("Model is required", 400);
        }

        String prompt = extractPromptFromRequest(request);
        if (StringUtils.isBlank(prompt)) {
            throw new WebApplicationException("Input is required", 400);
        }

        String sessionId = resolveSessionId(modelId, request.user() != null ? request.user() : "anonymous");
        String responseId = "resp_" + UUID.randomUUID();
        long createdAt = System.currentTimeMillis() / 1000;

        return agentExecutionService.run(modelId, sessionId, prompt)
            .map(new ResponsesEventMapper(responseId, modelId, createdAt)::mapEvent)
            .switchMap(e -> e);
    }

    private String extractPromptFromRequest(ResponsesRequest request) {
        String input = request.input();
        if (StringUtils.isNotBlank(input)) {
            return input;
        }
        final List<Message> messages = request.messages();
        if (CollectionUtils.isNotEmpty(messages)) {
            return extractUserMessage(messages);
        }
        return null;
    }

    private String extractUserMessage(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if ("user".equalsIgnoreCase(msg.role())) {
                return msg.content() instanceof String text ? text : msg.content().toString();
            }
        }
        return null;
    }

    private String resolveSessionId(String agentId, String user) {
        if (StringUtils.isNotBlank(user)) {
            return "session-" + agentId + "-" + user;
        }
        return UUID.randomUUID().toString();
    }
}
