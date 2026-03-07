package com.agentengine.engine.tools.multiagent;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolRiskLevel;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.google.adk.events.Event;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Flowable;

import java.util.*;

public final class DelegateToAgentTool extends Tool {
  private static final String TOOL_NAME = "delegate_to_agent";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "Delegate a sub-task to another agent and return its result.",
          List.of(ALL),
          Map.of(),
          ToolRiskLevel.MEDIUM);

  private final AgentContext agentContext;
  private final AgentExecutionService agentExecutionService;
  private final MultiAgentToolConfig config;

  public DelegateToAgentTool(
      final AgentContext agentContext,
      final AgentExecutionService agentExecutionService,
      final MultiAgentToolConfig config) {
    super(DESCRIPTOR);
    this.agentContext = agentContext;
    this.agentExecutionService = agentExecutionService;
    this.config = config;
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolSchema(name = "agent_id", description = "Target agent id for delegation")
          final String targetAgentId,
      @ToolSchema(name = "message", description = "Task message to delegate")
          final String message) {
    if (StringUtils.isBlank(targetAgentId)) {
      return Map.of("error", "agent_id is required");
    }
    if (StringUtils.isBlank(message)) {
      return Map.of("error", "message is required");
    }
    final String sourceAgentId = agentContext == null ? null : agentContext.agentId();
    if (!config.allowSelfDelegation() && Objects.equals(sourceAgentId, targetAgentId)) {
      return Map.of("error", "Delegation to the same agent is not allowed by policy.");
    }
    if (!CollectionUtils.nullSafeList(config.allowedAgents()).contains(targetAgentId)) {
      return Map.of(
          "error",
          "Delegation target is not in allowed_agents policy list: " + targetAgentId);
    }

    final String sourceSessionId =
        toolContext == null || toolContext.invocationContext() == null || toolContext.invocationContext().session() == null
            ? null
            : toolContext.invocationContext().session().id();
    final String delegatedSessionId =
        MultiAgentUtils.buildDelegationSessionId(
            sourceSessionId, targetAgentId, config.useSourceSessionPrefix());

    final AgentRequest request = new AgentRequest();
    request.setType(AgentRequest.RequestType.STREAM_AGUI_EVENTS.name());
    request.setAgentId(targetAgentId);
    request.setSessionId(delegatedSessionId);
    request.setMessage(message);

    final List<String> chunks = new ArrayList<>();
    int eventCount = 0;
    try {
      final Flowable<Event> stream = agentExecutionService.run(request);
      for (final Event event : stream.blockingIterable()) {
        eventCount++;

        final String text = event.content().orElse(Content.builder().build()).text();
        if (StringUtils.isNotBlank(text)) {
          chunks.add(text);
        }
      }
    } catch (Exception ex) {
      return Map.of(
          "status",
          "error",
          "delegated_agent_id",
          targetAgentId,
          "session_id",
          delegatedSessionId,
          "error",
          ex.getMessage());
    }

    final String output = MultiAgentUtils.truncate(String.join("\n", chunks), config.maxOutputChars());
    final Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", "success");
    result.put("delegated_agent_id", targetAgentId);
    result.put("session_id", delegatedSessionId);
    result.put("event_count", eventCount);
    result.put("output", output);
    return result;
  }
}
