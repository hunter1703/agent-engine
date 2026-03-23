package com.agentengine.runtime.tools;

import com.agentengine.runtime.api.tools.ToolDescriptor;
import com.agentengine.runtime.hitl.SessionPauseKind;
import com.agentengine.runtime.plugin.annotations.ToolSchema;
import com.agentengine.runtime.plugin.tools.Tool;
import com.agentengine.runtime.plugin.utils.ToolUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.tools.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HumanInTheLoopTool extends Tool {
  public static final String TOOL_NAME = "human_in_the_loop";
  public static final String PROMPT = "prompt";
  public static final String KIND = "kind";
  public static final String RESPONSE_OPTIONS = "options";
  public static final String CONTEXT = "context";
  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME, "Request input from the user.", Map.of());

  public HumanInTheLoopTool() {
    super(DESCRIPTOR);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true) final ToolContext toolContext,
      @ToolSchema(name = PROMPT, description = "Prompt shown to the user") final String prompt,
      @ToolSchema(name = KIND, description = "Input kind: TEXT or DECISION") final String kind,
      @ToolSchema(name = RESPONSE_OPTIONS, description = "Optional answer options the user can choose from", optional = true) List<String> options,
      @ToolSchema(name = CONTEXT, description = "Optional context metadata for the pause", optional = true) final Map<String, Object> context) {
    if (toolContext == null) {
      return Map.of("message", "Invocation context is not available for request_human_input.");
    }
    final SessionPauseKind pauseKind = SessionPauseKind.valueOfOrDefault(kind);
    final ToolConfirmation confirmation = toolContext.toolConfirmation().orElse(null);
    if (confirmation != null) {
      return confirm(confirmation, pauseKind);
    }

    return requestConfirmation(toolContext, prompt, options, context, pauseKind);
  }

  private static Map<String, Object> requestConfirmation(final ToolContext toolContext, final String prompt, List<String> options,
      final Map<String, Object> context, final SessionPauseKind pauseKind) {
    final String sanitizedPrompt = StringUtils.isNotBlank(prompt) ? prompt.trim() : "User input is required to continue.";
    options = CollectionUtils.nullSafeList(options).stream().filter(StringUtils::isNotBlank).map(String::trim)
        .filter(StringUtils::isNotBlank).distinct().toList();
    final Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(KIND, pauseKind.name());
    if (CollectionUtils.isNotEmpty(options)) {
      payload.put(RESPONSE_OPTIONS, options);
    }
    if (CollectionUtils.isNotEmpty(context)) {
      payload.put(CONTEXT, context);
    }
    ToolUtils.requestConfirmationAndPause(toolContext, sanitizedPrompt, payload);
    return Map.of();
  }

  private static Map<String, Object> confirm(final ToolConfirmation confirmation, final SessionPauseKind pauseKind) {
    return switch (pauseKind) {
      // The decision is surfaced in the function response so the LLM can reason about
      // whether to proceed or abort — especially critical in the rejection case where
      // the LLM must not continue with the originally requested action.
      case DECISION -> Map.of("decision", confirmation.confirmed() ? "ALLOW" : "DISALLOW");
      case TEXT -> {
        if (!confirmation.confirmed()) {
          yield Map.of("status", "cancelled");
        }
        // noinspection unchecked
        final String answer = CollectionUtils.getStringValueFromMap((Map<String, Object>) confirmation.payload(), "answer");
        yield Map.of("status", "answered", "answer", Objects.requireNonNull(answer));
      }
      case UNKNOWN -> // noinspection unchecked
        CollectionUtils.nullSafeMap((Map<String, Object>) confirmation.payload());
    };
  }
}
