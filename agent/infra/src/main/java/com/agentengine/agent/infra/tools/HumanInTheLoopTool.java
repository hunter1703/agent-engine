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
          "Pauses the run to ask the user for something only they can give: missing information, a "
              + "choice between alternatives, or approval of a destructive or irreversible action. "
              + "Do not ask for what you can work out or look up. Give any analysis in your normal "
              + "reply before calling it; the call carries only the question. "
              + "Returns { answer }, { decision: ALLOW or DISALLOW } for an approval, or "
              + "{ status: cancelled }.",
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
                  "The question itself and nothing else. Put the choices in options, not here.")
          final String question,
      @ToolArg(
              name = KIND,
              description =
                  "TEXT for an answer or a choice; DECISION for an approval (ALLOW or DISALLOW).",
              enums = {"TEXT", "DECISION"})
          final String kind,
      @ToolArg(
              name = RESPONSE_OPTIONS,
              description =
                  "With kind TEXT: the choices to offer, e.g. ['Option A', 'Option B']. Leave empty for an open question.",
              optional = true)
          List<String> options,
      @ToolArg(
              name = "allowCustomAnswer",
              description =
                  "With options: also accept a free-text answer. Defaults to true when there are no options.",
              optional = true)
          final Boolean allowCustomAnswer,
      @ToolArg(
              name = "isMultiSelect",
              description = "With options: allow choosing several. Defaults to one choice.",
              optional = true)
          final Boolean isMultiSelect,
      @ToolArg(
              name = CONTEXT,
              description = "Machine-readable state about what is pending. It is not shown to the user.",
              optional = true)
          final Map<String, Object> context) {
    if (toolContext == null) {
      return ToolOutput.direct(Map.of("error", "Invocation context is not available."));
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
        yield Map.of("answer", Objects.requireNonNull(answer));
      }
      case UNKNOWN -> // noinspection unchecked
          CollectionUtils.nullSafeMap((Map<String, Object>) confirmation.payload());
    };
  }
}
