package com.agentengine.runtime.tools;

import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.ConfirmationKind;
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
    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            Constants.HITL_TOOL_NAME,
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

    public Map<String, Object> execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
                    final ToolContext toolContext,
            @ToolSchema(
                            name = PROMPT,
                            description =
                                    "ONLY a targeted and concise question requesting ONLY the information that is genuinely absent and blocks execution. Do NOT restate the user's request, ask for confirmation of stated intent, or use this field for answers, explanations, or error messages or greetings (like \"Hello\", \"Thank you\", etc.).")
                    final String prompt,
            @ToolSchema(
                            name = KIND,
                            description =
                                    "Type of input required. TEXT: critical information is missing with no reasonable default. DECISION: user must approve a destructive action or choose between mutually exclusive options with no clear preference. Do NOT use for routine confirmations.")
                    final String kind,
            @ToolSchema(
                            name = RESPONSE_OPTIONS,
                            description =
                                    "Required when kind is DECISION. A small list of explicit user-selectable choices, such as ['Yes', 'No'] or ['Use Option A', 'Use Option B']. Do not include this for TEXT unless the choices are genuinely constrained.",
                            optional = true)
                    List<String> options,
            @ToolSchema(
                            name = CONTEXT,
                            description =
                                    "Optional structured metadata describing why user input is required, what is blocked, and what decision is pending. For machine-readable state only. Do NOT place user-facing explanations or final answers here.",
                            optional = true)
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
        requestConfirmation(toolContext, prompt, options, context, pauseKind);
        return null;
    }

    private void requestConfirmation(
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
    }

    private static Map<String, Object> confirm(
            final ToolConfirmation confirmation, final ConfirmationKind confirmationKind) {
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
