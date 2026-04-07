package com.agentengine.runtime.tools;

import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.util.agents.beans.ConfirmationKind;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
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
                    Constants.HITL_TOOL_NAME,
                    "Pause and ask the user for required input ONLY when you cannot make MEANINGFUL progress WITHOUT human clarification, preference, approval, or a decision. " +
                            "DO NOT use this tool for errors, retries, status updates, confirmations that are not required, or to present a final answer. " +
                            "If the request can be completed reasonably using available context or sensible defaults, do not call this tool.",
                    Map.of()
            );

    public HumanInTheLoopTool() {
        super(DESCRIPTOR);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
                    final ToolContext toolContext,
            @ToolSchema(name = PROMPT, description = "A short, specific question asking only for the missing information, preference, approval, or decision needed to continue. Do NOT use this field to provide a final answer, explain internal errors, or include large amounts of generated content.") final String prompt,
            @ToolSchema(name = KIND, description = "Type of user input required. Use TEXT when the user must provide missing information or clarification. Use DECISION when the user must choose, approve, reject, or select from explicit options.") final String kind,
            @ToolSchema(
                            name = RESPONSE_OPTIONS,
                            description = "Required when kind is DECISION. A small list of explicit user-selectable choices, such as ['Yes', 'No'] or ['Use Option A', 'Use Option B']. Do not include this for TEXT unless the choices are genuinely constrained.",
                            optional = true)
                    List<String> options,
            @ToolSchema(name = CONTEXT, description = "Optional structured metadata describing why user input is required, what is blocked, and what decision is pending. For machine-readable state only. Do NOT place user-facing explanations or final answers here.", optional = true)
                    final Map<String, Object> context) {
        if (toolContext == null) {
            return Map.of("message", "Invocation context is not available for request_human_input.");
        }
        final ConfirmationKind pauseKind = ConfirmationKind.valueOfOrDefault(kind);
        final ToolConfirmation confirmation = toolContext.toolConfirmation().orElse(null);
        if (confirmation != null) {
            LOG.info(
                    "Consuming HITL confirmation kind={} confirmed={} payloadPresent={}",
                    pauseKind,
                    confirmation.confirmed(),
                    confirmation.payload() != null);
            return confirm(confirmation, pauseKind);
        }

        LOG.info("Requesting HITL confirmation kind={}", pauseKind);
        return requestConfirmation(toolContext, prompt, options, context, pauseKind);
    }

    private Map<String, Object> requestConfirmation(
            final ToolContext toolContext,
            final String prompt,
            List<String> options,
            final Map<String, Object> context,
            final ConfirmationKind pauseKind) {
        final String sanitizedPrompt =
                StringUtils.isNotBlank(prompt) ? prompt.trim() : "User input is required to continue.";
        options = CollectionUtils.nullSafeList(options).stream()
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
        if (CollectionUtils.isNotEmpty(context)) {
            payload.put(CONTEXT, context);
        }
        toolContext.requestConfirmation(sanitizedPrompt, payload);
        return Map.of();
    }

    private static Map<String, Object> confirm(final ToolConfirmation confirmation, final ConfirmationKind confirmationKind) {
        return switch (confirmationKind) {
            // The decision is surfaced in the function response so the LLM can reason about
            // whether to proceed or abort — especially critical in the rejection case where
            // the LLM must not continue with the originally requested action.
            case DECISION -> Map.of("decision", confirmation.confirmed() ? "ALLOW" : "DISALLOW");
            case TEXT -> {
                if (!confirmation.confirmed()) {
                    yield Map.of("status", "cancelled");
                }
                // noinspection unchecked
                final String answer =
                        CollectionUtils.getStringValueFromMap((Map<String, Object>) confirmation.payload(), "answer");
                yield Map.of("status", "answered", "answer", Objects.requireNonNull(answer));
            }
            case UNKNOWN -> // noinspection unchecked
                CollectionUtils.nullSafeMap((Map<String, Object>) confirmation.payload());
        };
    }
}
