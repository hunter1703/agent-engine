package com.agentengine.util.agents.agui;

import static com.agentengine.util.agents.Constants.ARG_ORIGINAL_FUNCTION_CALL;
import static com.google.adk.flows.llmflows.Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME;

import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.InterruptKind;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agui.community.core.event.*;
import com.agui.community.core.event.CustomEvent;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.ToolCallEndEvent;
import com.agui.community.core.event.ToolCallResultEvent;
import com.agui.community.core.message.Role;
import com.google.adk.events.ToolConfirmation;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AGUIToolCallMapper {

    private static final Logger LOG = LoggerFactory.getLogger(AGUIToolCallMapper.class);
    private static final String ARG_TOOL_CONFIRMATION = "toolConfirmation";

    private final AGUIMapperState state;

    public AGUIToolCallMapper(final AGUIMapperState state) {
        this.state = state;
    }

    public Flowable<Event> mapToolCall(final FunctionCall call) {
        final String callName = call.name().orElse("");
        if (REQUEST_CONFIRMATION_FUNCTION_CALL_NAME.equals(callName)) {
            return mapInterruptCall(call);
        } else if (Constants.HITL_TOOL_NAME.equals(callName)) {
            return Flowable.empty();
        }
        final String callId = call.id().orElseGet(() -> UUID.randomUUID().toString());
        final String toolName = call.name().orElse("unknown");
        final Map<String, Object> args = call.args().orElse(Map.of());

        state.rememberToolCallParentStep(callId);
        LOG.debug("Processing tool call mapping - callId='{}', toolName='{}'", callId, toolName);

        final ToolCallStartEvent start =
                new ToolCallStartEvent(callId, toolName, state.currentStepName(), state.timestamp(), null);
        final ToolCallArgsEvent argsEvent =
                new ToolCallArgsEvent(callId, JsonUtils.toJson(args), state.timestamp(), null);
        final ToolCallEndEvent end = new ToolCallEndEvent(callId, state.timestamp(), null);
        return Flowable.just(start, argsEvent, end);
    }

    public Flowable<Event> mapToolResponse(final FunctionResponse response) {
        final String responseName = response.name().orElse("");
        if (REQUEST_CONFIRMATION_FUNCTION_CALL_NAME.equals(responseName)) {
            return mapResumedResponse(response);
        } else if (Constants.HITL_TOOL_NAME.equals(responseName)) {
            return Flowable.empty();
        }
        final String callId = response.id().orElse(UUID.randomUUID().toString());
        final String contentResult = JsonUtils.toJson(response.response().orElse(Map.of()));

        LOG.debug("Processing tool response mapping - callId='{}'", callId);

        final ToolCallResultEvent result = new ToolCallResultEvent(
                state.consumeToolCallParentStep(callId), callId, contentResult, Role.TOOL, state.timestamp(), null);

        LOG.debug("Generated output event - eventType=ToolCallResultEvent, callId={}", result.toolCallId());
        return Flowable.just(result);
    }

    private Flowable<Event> mapInterruptCall(final FunctionCall call) {
        final String interruptId = call.id().orElseThrow();
        final Map<String, Object> args = CollectionUtils.nullSafeMap(call.args().orElse(Map.of()));

        final FunctionCall originalFunctionCall =
                Objects.requireNonNull(CollectionUtils.getValueFromMap(args, ARG_ORIGINAL_FUNCTION_CALL));
        // paused by a tool whose interrupt is not supposed to be answered by the user, so suppress that event
        if (Objects.equals(
                Constants.AWAIT_AGENT_TOOL_NAME, originalFunctionCall.name().orElse(null))) {
            return Flowable.empty();
        }
        final String originalToolCallId = originalFunctionCall.id().orElseThrow();

        final ToolConfirmation toolConfirmation =
                Objects.requireNonNull(CollectionUtils.getValueFromMap(args, ARG_TOOL_CONFIRMATION));
        final String prompt = toolConfirmation.hint();
        @SuppressWarnings("unchecked")
        final List<String> options =
                CollectionUtils.getListFromMap((Map<String, Object>) toolConfirmation.payload(), "options");

        final InterruptKind kind = Objects.equals(
                        Constants.HITL_TOOL_NAME, originalFunctionCall.name().orElse(null))
                ? InterruptKind.TEXT
                : InterruptKind.DECISION;
        final CustomEvent event = AGUIUtils.buildInterruptRequestedEvent(
                interruptId, prompt, originalToolCallId, options, kind, state.timestamp());
        LOG.debug(
                "Generated output event - eventType=interrupt_requested, interruptId={}, originalToolCallId={}",
                interruptId,
                originalToolCallId);
        return Flowable.just(event);
    }

    public Flowable<Event> mapResumedResponse(final FunctionResponse response) {
        final String interruptId = response.id().orElse(null);
        final ToolConfirmation toolConfirmation = JsonUtils.fromMap(
                CollectionUtils.nullSafeMap(response.response().orElse(Map.of())), ToolConfirmation.class);
        final FunctionCall interruptRequestedCall = state.getInterruptRequestedCall(interruptId);
        final Map<String, Object> args =
                CollectionUtils.nullSafeMap(interruptRequestedCall.args().orElse(Map.of()));

        final FunctionCall originalFunctionCall =
                Objects.requireNonNull(CollectionUtils.getValueFromMap(args, ARG_ORIGINAL_FUNCTION_CALL));
        // paused by a tool whose interrupt is not supposed to be answered by the user, so suppress that event
        if (Objects.equals(
                Constants.AWAIT_AGENT_TOOL_NAME, originalFunctionCall.name().orElse(null))) {
            return Flowable.empty();
        }
        final boolean accepted = toolConfirmation != null && toolConfirmation.confirmed();
        @SuppressWarnings("unchecked")
        final String answer = CollectionUtils.getStringValueFromMap(
                (Map<String, Object>) (toolConfirmation != null ? toolConfirmation.payload() : null), "answer");

        final CustomEvent event = AGUIUtils.buildResumedEvent(interruptId, accepted, answer, state.timestamp());
        LOG.debug("Generated output event - eventType=resumed, interruptId={}, accepted={}", interruptId, accepted);
        return Flowable.just(event);
    }
}
