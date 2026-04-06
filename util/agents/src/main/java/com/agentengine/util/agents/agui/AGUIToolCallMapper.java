package com.agentengine.util.agents.agui;

import static com.google.adk.flows.llmflows.Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME;

import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.ConfirmationKind;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.ToolCallArgsEvent;
import com.agui.core.event.ToolCallEndEvent;
import com.agui.core.event.ToolCallResultEvent;
import com.agui.core.event.ToolCallStartEvent;
import com.agui.core.message.Role;
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
    private static final String ARG_ORIGINAL_FUNCTION_CALL = "originalFunctionCall";
    private static final String ARG_TOOL_CONFIRMATION = "toolConfirmation";

    private final AGUIMapperState state;
    private final AGUIEventDecorator decorator;

    public AGUIToolCallMapper(final AGUIMapperState state, final AGUIEventDecorator decorator) {
        this.state = state;
        this.decorator = decorator;
    }

    public Flowable<BaseEvent> mapToolCall(final FunctionCall call) {
        final String callName = call.name().orElse("");
        if (REQUEST_CONFIRMATION_FUNCTION_CALL_NAME.equals(callName)) {
            return mapConfirmationCall(call);
        } else if (Constants.HITL_TOOL_NAME.equals(callName)) {
            return Flowable.empty();
        }
        final String callId = call.id().orElseGet(() -> UUID.randomUUID().toString());
        final String toolName = call.name().orElse("unknown");
        final Map<String, Object> args = call.args().orElse(Map.of());

        state.rememberToolCallParentStep(callId);
        LOG.debug("Processing tool call mapping - callId='{}', toolName='{}'", callId, toolName);

        Flowable<BaseEvent> flowable = Flowable.empty();
        final ToolCallStartEvent start = new ToolCallStartEvent();
        start.setToolCallId(callId);
        start.setToolCallName(toolName);
        start.setParentMessageId(state.currentStepName());
        flowable = flowable.concatWith(Flowable.just(decorator.decorate(start)));

        final ToolCallArgsEvent argsEvent = new ToolCallArgsEvent();
        argsEvent.setToolCallId(callId);
        argsEvent.setDelta(JsonUtils.toJson(args));
        flowable = flowable.concatWith(Flowable.just(decorator.decorate(argsEvent)));

        final ToolCallEndEvent end = new ToolCallEndEvent();
        end.setToolCallId(callId);
        return flowable.concatWith(Flowable.just(decorator.decorate(end)));
    }

    public Flowable<BaseEvent> mapToolResponse(final FunctionResponse response) {
        final String responseName = response.name().orElse("");
        if (REQUEST_CONFIRMATION_FUNCTION_CALL_NAME.equals(responseName)) {
            return mapConfirmedResponse(response);
        } else if (Constants.HITL_TOOL_NAME.equals(responseName)) {
            return Flowable.empty();
        }
        final String callId = response.id().orElse(UUID.randomUUID().toString());
        final String contentResult = JsonUtils.toJson(response.response().orElse(Map.of()));

        LOG.debug("Processing tool response mapping - callId='{}'", callId);

        final ToolCallResultEvent result = new ToolCallResultEvent();
        result.setToolCallId(callId);
        result.setContent(contentResult);
        result.setRole(Role.tool);
        result.setMessageId(state.consumeToolCallParentStep(callId));

        LOG.debug("Generated output event - eventType=ToolCallResultEvent, callId={}", result.getToolCallId());
        return Flowable.just(decorator.decorate(result));
    }

    private Flowable<BaseEvent> mapConfirmationCall(final FunctionCall call) {
        final String confirmationId = call.id().orElseThrow();
        final Map<String, Object> args = CollectionUtils.nullSafeMap(call.args().orElse(Map.of()));

        final FunctionCall originalFunctionCall = CollectionUtils.getValueFromMap(args, ARG_ORIGINAL_FUNCTION_CALL);
        if (originalFunctionCall == null) {
            LOG.warn("Missing originalFunctionCall in confirmation args - args='{}'", JsonUtils.toJson(args));
            return Flowable.empty();
        }
        
        final String originalToolCallId = originalFunctionCall.id().orElseThrow();

        final ToolConfirmation toolConfirmation = CollectionUtils.getValueFromMap(args, ARG_TOOL_CONFIRMATION);
        if (toolConfirmation == null) {
            LOG.warn("Missing toolConfirmation in confirmation args - args='{}'", JsonUtils.toJson(args));
            return Flowable.empty();
        }
        
        final String prompt = toolConfirmation.hint();
        @SuppressWarnings("unchecked")
        final List<String> options = CollectionUtils.getListFromMap(
                (Map<String, Object>) toolConfirmation.payload(), "options");

        final ConfirmationRequestedEvent event =
                new ConfirmationRequestedEvent(confirmationId, prompt, originalToolCallId, options, Objects.equals(Constants.HITL_TOOL_NAME, originalFunctionCall.name().orElse(null)) ? ConfirmationKind.TEXT : ConfirmationKind.DECISION);
        decorator.decorate(event);
        LOG.debug(
                "Generated output event - eventType=ConfirmationRequestedEvent, confirmationId={}, originalToolCallId={}",
                confirmationId,
                originalToolCallId);
        return Flowable.just(event);
    }

    public Flowable<BaseEvent> mapConfirmedResponse(final FunctionResponse response) {
        final String confirmationId = response.id().orElse(null);
        final ToolConfirmation toolConfirmation = JsonUtils.fromMap(
                CollectionUtils.nullSafeMap(response.response().orElse(Map.of())), ToolConfirmation.class);
        final boolean confirmed = toolConfirmation != null && toolConfirmation.confirmed();
        @SuppressWarnings("unchecked")
        final String answer = CollectionUtils.getStringValueFromMapSafe(
                (Map<String, Object>) (toolConfirmation != null ? toolConfirmation.payload() : null), "answer");

        final ConfirmedEvent event = new ConfirmedEvent(confirmationId, confirmed, answer);
        decorator.decorate(event);
        LOG.debug(
                "Generated output event - eventType=ConfirmedEvent, confirmationId={}, confirmed={}",
                confirmationId,
                confirmed);
        return Flowable.just(event);
    }
}
