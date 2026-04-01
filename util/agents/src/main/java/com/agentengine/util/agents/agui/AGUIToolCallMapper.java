package com.agentengine.util.agents.agui;

import com.agentengine.util.common.JsonUtils;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.ToolCallArgsEvent;
import com.agui.core.event.ToolCallEndEvent;
import com.agui.core.event.ToolCallResultEvent;
import com.agui.core.event.ToolCallStartEvent;
import com.agui.core.message.Role;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AGUIToolCallMapper {

    private static final Logger LOG = LoggerFactory.getLogger(AGUIToolCallMapper.class);

    private final AGUIMapperState state;
    private final AGUIEventDecorator decorator;

    public AGUIToolCallMapper(final AGUIMapperState state, final AGUIEventDecorator decorator) {
        this.state = state;
        this.decorator = decorator;
    }

    public Flowable<BaseEvent> mapToolCall(final FunctionCall call) {
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
}
