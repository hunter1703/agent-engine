package com.agentengine.agent.infra.tools;

import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.InterruptKind;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.tools.ToolContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HumanInTheLoopTool extends Tool {
  private static final Logger LOG = LoggerFactory.getLogger(HumanInTheLoopTool.class);
  public static final String PROMPT = "prompt";
  public static final String KIND = "kind";
  public static final String RESPONSE_OPTIONS = "options";
  public static final String CONTEXT = "context";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.HITL,
          "Pauses execution to request input from a human operator. Use ONLY when execution is genuinely blocked "
              + "by information that cannot be inferred, defaulted, or derived from context: (1) a required "
              + "input is absent and no reasonable default exists; (2) the operator must choose between "
              + "mutually exclusive alternatives with no stated or inferrable preference; or (3) a destructive "
              + "or irreversible action requires explicit approval before proceeding. "
              + "Do NOT use to confirm intent already stated, present unnecessary choices, report errors, "
              + "deliver answers, or provide status updates. If the request can be fulfilled as-stated, "
              + "do not call this tool.",
          Map.of());

  public HumanInTheLoopTool() {
    super(DESCRIPTOR, true);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = PROMPT,
              description =
                  "ONLY a targeted and concise question requesting ONLY the information that is genuinely absent and blocks execution. Do NOT restate the user's request, ask for confirmation of stated intent, or use this field for answers, explanations, or error messages or greetings (like \"Hello\", \"Thank you\", etc.).")
          final String prompt,
      @ToolArg(
              name = KIND,
              description =
                  "Type of input required. TEXT: expecting information, choices, or custom answers. DECISION: user must only approve or reject an action (ALLOW/DISALLOW). DECISION requires no other parameters.")
          final String kind,
      @ToolArg(
              name = RESPONSE_OPTIONS,
              description =
                  "Used when kind is TEXT. A list of explicit user-selectable choices (e.g., ['Alice', 'Bob']).",
              optional = true)
          List<String> options,
      @ToolArg(
              name = "allowCustomAnswer",
              description =
                  "Used when kind is TEXT. If true, allows the user to provide a free-form text answer. MUST be true if kind is TEXT and options are empty. MUST be false or omitted if the user is strictly required to select from the provided options.",
              optional = true)
          final Boolean allowCustomAnswer,
      @ToolArg(
              name = "isMultiSelect",
              description =
                  "Used when kind is TEXT and options are provided. If true, allows the user to select multiple options. MUST be false or omitted if the user is required to select exactly one option.",
              optional = true)
          final Boolean isMultiSelect,
      @ToolArg(
              name = CONTEXT,
              description =
                  "Optional structured metadata describing why user input is required, what is blocked, and what decision is pending. For machine-readable state only. Do NOT place user-facing explanations or final answers here.",
              optional = true)
          final Map<String, Object> context) {
    if (toolContext == null) {
      return ToolOutput.direct(
          Map.of("message", "Invocation context is not available for request_human_input."));
    }
    final InterruptKind pauseKind = InterruptKind.valueOfOrDefault(kind);
    if (pauseKind == InterruptKind.DECISION) {
      if (CollectionUtils.isNotEmpty(options)) {
        return ToolOutput.direct(
            Map.of(
                "error",
                "options must be empty when kind is DECISION. Either omit options or use TEXT instead."));
      }
      if (Boolean.TRUE.equals(allowCustomAnswer)) {
        return ToolOutput.direct(
            Map.of(
                "error",
                "allowCustomAnswer must be false/null when kind is DECISION. Either omit allowCustomAnswer or use TEXT instead."));
      }
      if (Boolean.TRUE.equals(isMultiSelect)) {
        return ToolOutput.direct(
            Map.of(
                "error",
                "isMultiSelect must be false/null when kind is DECISION. Either omit isMultiSelect or use TEXT instead."));
      }
    } else if (pauseKind == InterruptKind.TEXT) {
      if (CollectionUtils.isEmpty(options) && !Boolean.TRUE.equals(allowCustomAnswer)) {
        return ToolOutput.direct(
            Map.of(
                "error",
                "allowCustomAnswer MUST be true when kind is TEXT and no options are provided."));
      }
    }

    final ToolConfirmation confirmation = toolContext.toolConfirmation().orElse(null);
    if (confirmation != null) {
      LOG.debug(
          "Consuming HITL interrupt kind={} confirmed={} payloadPresent={}",
          pauseKind,
          confirmation.confirmed(),
          confirmation.payload() != null);
      return ToolOutput.direct(resolveInterruptResult(confirmation, pauseKind));
    }

    LOG.debug("Requesting HITL interrupt kind={}", pauseKind);
    requestInterrupt(
        toolContext, prompt, options, allowCustomAnswer, isMultiSelect, context, pauseKind);
    return ToolOutput.empty();
  }

  private void requestInterrupt(
      final ToolContext toolContext,
      final String prompt,
      List<String> options,
      final Boolean allowCustomAnswer,
      final Boolean isMultiSelect,
      final Map<String, Object> context,
      final InterruptKind pauseKind) {
    final String sanitizedPrompt =
        StringUtils.isNotBlank(prompt) ? prompt.trim() : "User input is required to continue.";
    options =
        CollectionUtils.nullSafeList(options).stream()
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .toList();
    final Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(KIND, pauseKind.name());
    if (CollectionUtils.isNotEmpty(options)) {
      payload.put(RESPONSE_OPTIONS, options);
    }
    if (Boolean.TRUE.equals(allowCustomAnswer)) {
      payload.put("allowCustomAnswer", true);
    }
    if (Boolean.TRUE.equals(isMultiSelect)) {
      payload.put("isMultiSelect", true);
    }
    if (CollectionUtils.isNotEmpty(context)) {
      payload.put(CONTEXT, context);
    }
    toolContext.requestConfirmation(sanitizedPrompt, payload);
  }

  private static Map<String, Object> resolveInterruptResult(
      final ToolConfirmation confirmation, final InterruptKind interruptKind) {
    return switch (interruptKind) {
      // The decision is surfaced in the function response so the LLM can reason about
      // whether to proceed or abort — especially critical in the rejection case where
      // the LLM must not continue with the originally requested action.
      case DECISION -> Map.of("decision", confirmation.confirmed() ? "ALLOW" : "DISALLOW");
      case TEXT -> {
        if (!confirmation.confirmed()) {
          yield Map.of(Constants.ToolStatus.STATUS, Constants.ToolStatus.CANCELLED);
        }
        // noinspection unchecked
        final String answer =
            CollectionUtils.getStringValueFromMap(
                (Map<String, Object>) confirmation.payload(), "answer");
        yield Map.of(
            Constants.ToolStatus.STATUS,
            Constants.ToolStatus.ANSWERED,
            "answer",
            Objects.requireNonNull(answer));
      }
      case UNKNOWN -> // noinspection unchecked
          CollectionUtils.nullSafeMap((Map<String, Object>) confirmation.payload());
    };
  }
}
