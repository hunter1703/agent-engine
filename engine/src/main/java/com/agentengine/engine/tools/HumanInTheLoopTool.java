package com.agentengine.engine.tools;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.hitl.ConfirmationDecision;
import com.agentengine.engine.hitl.SessionPauseKind;
import com.agentengine.engine.plugin.annotations.ToolSchema;
import com.agentengine.engine.plugin.tools.Tool;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.tools.ToolContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HumanInTheLoopTool extends Tool {
  public static final String TOOL_NAME = "human_in_the_loop";
  public static final String ARG_PROMPT = "prompt";
  public static final String ARG_KIND = "kind";
  public static final String ARG_OPTIONS = "options";
  public static final String ARG_CONTEXT = "context";
  public static final List<String> DECISION_OPTIONS = List.of(ConfirmationDecision.ALLOW.name(), ConfirmationDecision.DISALLOW.name());
  private static final String STATUS_ERROR = "error";
  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME, "Request input from the user.", Map.of());

  public HumanInTheLoopTool() {
    super(DESCRIPTOR);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true) final ToolContext toolContext,
      @ToolSchema(name = ARG_PROMPT, description = "Prompt shown to the user") final String prompt,
      @ToolSchema(name = ARG_KIND, description = "Input kind: TEXT or DECISION") final String kind,
      @ToolSchema(name = ARG_OPTIONS, description = "Optional answer options the user can choose from", optional = true) final List<String> options,
      @ToolSchema(name = ARG_CONTEXT, description = "Optional context metadata for the pause", optional = true) final Map<String, Object> context) {
    if (toolContext == null) {
      return Map.of("status", STATUS_ERROR, "message", "Invocation context is not available for request_human_input.");
    }
    final SessionPauseKind pauseKind = SessionPauseKind.valueOfOrDefault(kind);
    final String sanitizedPrompt = StringUtils.isNotBlank(prompt) ? prompt.trim() : "User input is required to continue.";
    final List<String> sanitizedOptions = sanitizeOptions(options, pauseKind);

    final ToolConfirmation confirmation = toolContext.toolConfirmation().orElse(null);
    if (confirmation != null) {
      // noinspection unchecked
      return (Map<String, Object>) confirmation.payload();
    }

    final Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(ARG_KIND, pauseKind.name());
    if (!sanitizedOptions.isEmpty()) {
      payload.put(ARG_OPTIONS, sanitizedOptions);
    }
    if (CollectionUtils.isNotEmpty(context)) {
      payload.put(ARG_CONTEXT, context);
    }
    toolContext.requestConfirmation(sanitizedPrompt, payload);
    return Map.of();
  }

  private static List<String> sanitizeOptions(final List<String> options, final SessionPauseKind pauseKind) {
    if (pauseKind == SessionPauseKind.DECISION) {
      return DECISION_OPTIONS;
    }
    if (options == null) {
      return List.of();
    }
    return options.stream().filter(StringUtils::isNotBlank).map(String::trim).filter(StringUtils::isNotBlank).distinct().toList();
  }
}
