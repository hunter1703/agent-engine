package com.agentengine.agent.infra.tools;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
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

@DiscoverableTool
public final class HumanInTheLoopTool extends Tool {
  private static final Logger LOG = LoggerFactory.getLogger(HumanInTheLoopTool.class);
  public static final String QUESTION = "question";
  public static final String KIND = "kind";
  public static final String RESPONSE_OPTIONS = "options";
  public static final String CONTEXT = "context";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.HITL,
          "Pauses execution to request clarification, missing information, user decisions, or approval. "
              + "Use when: (1) key evaluation criteria, definitions, or user preferences are ambiguous or underspecified and "
              + "guessing would yield an arbitrary or misaligned result; "
              + "(2) a required input is absent and cannot be derived from context; "
              + "(3) presenting alternatives, choices, or suggestions for the user to decide between (provide the choices in options; supports both single-selection and multi-selection via isMultiSelect); or "
              + "(4) a destructive or irreversible action requires explicit approval. "
              + "Deliver any preliminary analysis or context in your regular text response BEFORE calling this tool. "
              + "Keep the question argument strictly to the direct question itself.",
          Map.of());

  public HumanInTheLoopTool() {
    super(DESCRIPTOR, true);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = QUESTION,
              description =
                  "The direct question asking for clarification, missing information, or a decision. "
                      + "Deliver all preliminary analysis, context, and reasoning in your text response before calling this tool. "
                      + "Do NOT include greetings, acknowledgments, or analysis here. "
                      + "Never embed choices, alternatives, or parenthetical suggestions (e.g., '(e.g., ...)') in this string; put them in options instead. "
                      + "Do NOT restate the user's request.")
          final String question,
      @ToolArg(
              name = KIND,
              description =
                  "Type of input required: TEXT for answers or multiple-choice selections; DECISION for binary approval (ALLOW/DISALLOW).",
              enums = {"TEXT", "DECISION"})
          final String kind,
      @ToolArg(
              name = RESPONSE_OPTIONS,
              description =
                  "Used when kind is TEXT. A list of user-selectable options, suggestions, or alternatives (e.g., ['Option A', 'Option B']). "
                      + "MUST be populated whenever asking the user to choose between alternatives or whenever providing candidate suggestions or common choices for the user to select from in the UI. "
                      + "Supports both single-choice (default) and multi-choice selections (set isMultiSelect to true to allow multiple selections). "
                      + "If open-ended input is also allowed alongside options, set allowCustomAnswer to true.",
              optional = true)
          List<String> options,
      @ToolArg(
              name = "allowCustomAnswer",
              description =
                  "Used when kind is TEXT. If true, allows the user to provide a free-form text answer. Defaults to true when options are empty. MUST be false or omitted if the user is strictly required to select from the provided options.",
              optional = true)
          final Boolean allowCustomAnswer,
      @ToolArg(
              name = "isMultiSelect",
              description =
                  "Used when kind is TEXT and options are provided. Set to true to allow the user to select multiple choices from the options list (multi-selection). Defaults to false (single selection where exactly one choice is selected).",
              optional = true)
          final Boolean isMultiSelect,
      @ToolArg(
              name = CONTEXT,
              description =
                  "Structured metadata describing why user input is required, what is blocked, and what decision is pending. For machine-readable state only. Do NOT place user-facing explanations or final answers here.",
              optional = true)
          final Map<String, Object> context) {
    if (toolContext == null) {
      return ToolOutput.direct(
          Map.of("message", "Invocation context is not available for request_human_input."));
    }
    final InterruptKind pauseKind = InterruptKind.valueOfOrDefault(kind);
    final boolean effectiveAllowCustomAnswer =
        allowCustomAnswer != null ? allowCustomAnswer : CollectionUtils.isEmpty(options);
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
      if (CollectionUtils.isEmpty(options) && !effectiveAllowCustomAnswer) {
        return ToolOutput.direct(
            Map.of(
                "error",
                "allowCustomAnswer cannot be false when kind is TEXT and no options are provided."));
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
        toolContext,
        question,
        options,
        effectiveAllowCustomAnswer,
        isMultiSelect,
        context,
        pauseKind);
    return ToolOutput.empty();
  }

  private void requestInterrupt(
      final ToolContext toolContext,
      final String question,
      List<String> options,
      final Boolean allowCustomAnswer,
      final Boolean isMultiSelect,
      final Map<String, Object> context,
      final InterruptKind pauseKind) {
    final String sanitizedQuestion =
        StringUtils.isNotBlank(question) ? question.trim() : "User input is required to continue.";
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
    toolContext.requestConfirmation(sanitizedQuestion, payload);
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
