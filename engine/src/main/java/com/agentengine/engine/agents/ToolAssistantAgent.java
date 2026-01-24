package com.agentengine.engine.agents;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.StateStore;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.PlanItem;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.ResourceUtils;
import com.agentengine.engine.api.utils.TemplateUtils;
import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.tools.ToolPromptUtils;
import com.agentengine.engine.utils.EngineUtils;
import java.util.List;
import java.util.Map;

public final class ToolAssistantAgent {
  private static final String TOOL_SCHEMA =
      ResourceUtils.loadResourceAsString("/schemas/hybrid/tool_call_schema.json");
  private static final String DEFAULT_SYSTEM_PROMPT = """
      You translate plan steps into concrete tool calls using the available tools.
      Use only the tools listed below and return JSON matching the provided schema.
      """;
  private static final String TOOL_SESSION_SUFFIX = "_tool";

  private final LLMModel model;
  private final List<Tool> tools;
  private final StateStore stateStore;

  public ToolAssistantAgent(final LLMModel model, final List<Tool> tools, final StateStore stateStore) {
    this.model = model;
    this.tools = CollectionUtils.nullSafeMutableList(tools);
    this.stateStore = stateStore;
  }

  public List<ToolCall> generateToolCalls(final String sessionId, final String runId, final PlanItem planItem,
      final AgentListener listener) {
    if (planItem == null) {
      return List.of();
    }
    final String prompt = TemplateUtils.renderTemplateForName("hybrid/tool_assistant_plan_json.txt",
        Map.of("tool_schema", TOOL_SCHEMA, "planItem", planItem.step()));
    final Message system = Message.system(STR."""
        # TOOL ASSISTANT
        \{DEFAULT_SYSTEM_PROMPT}

        # TOOLS
        \{ToolPromptUtils.buildToolMessage(tools)}
        """);
    final Message user = Message.user(prompt);
    stateStore.appendMessage(toolSessionId(sessionId), runId, user);
    final Message response = model.generate(List.of(system, user));
    final Message sanitized = EngineUtils.sanitizeMessage(response, model.responseFormat(), model.thoughtsEnabled(),
        model.thoughtsStartTag(), model.thoughtsEndTag());
    stateStore.appendMessage(toolSessionId(sessionId), runId, sanitized);
    return CollectionUtils.nullSafeList(sanitized.getToolCalls());
  }

  private static String toolSessionId(final String sessionId) {
    return sessionId + TOOL_SESSION_SUFFIX;
  }
}
