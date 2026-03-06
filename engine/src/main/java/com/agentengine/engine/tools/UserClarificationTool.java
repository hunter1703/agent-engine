package com.agentengine.engine.tools;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.AgentTool;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.utils.HitlStateUtils;
import com.google.adk.tools.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@AgentTool
public final class UserClarificationTool extends Tool {
  private static final String TOOL_NAME = "user_clarification";
  private static final String DEFAULT_QUESTION = "Please provide clarification to continue.";
  private static final String REASON = "user_clarification";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(TOOL_NAME, "Request clarification from the user.", List.of(ALL), Map.of());

  public UserClarificationTool() {
    super(DESCRIPTOR);
  }

  public static Map<String, Object> clarifyFromUser(
      final String question, final List<String> options) {
    final String prompt = StringUtils.isNotBlank(question) ? question : DEFAULT_QUESTION;
    final Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("status", "paused");
    payload.put("clarification", prompt);
    if (!options.isEmpty()) {
      payload.put("options", options);
    }
    return Map.copyOf(payload);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolSchema(name = "question", description = "Clarifying question to present to the user")
          final String question,
      @ToolSchema(
              name = "options",
              description = "Optional answer options the user can choose from",
              optional = true)
          final List<String> options) {
    final String prompt = StringUtils.isNotBlank(question) ? question : DEFAULT_QUESTION;
    final List<String> sanitized = sanitizeOptions(options);
    if (toolContext != null && toolContext.invocationContext() != null) {
      HitlStateUtils.pause(toolContext.invocationContext(), prompt, sanitized, REASON);
    }
    return clarifyFromUser(prompt, sanitized);
  }

  private static List<String> sanitizeOptions(final List<String> options) {
    if (options == null) {
      return List.of();
    }
    return options.stream()
        .filter(StringUtils::isNotBlank)
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .distinct()
        .toList();
  }
}
