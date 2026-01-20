package com.agentengine.interfaces.rest.services;

import com.agentengine.commons.utils.CollectionUtils;
import com.agui.core.agent.AgentSubscriber;
import com.agui.core.agent.AgentSubscriberParams;
import com.agui.core.event.*;
import com.agui.core.message.BaseMessage;
import com.agui.core.state.State;
import com.agui.core.tool.ToolCall;
import io.smallrye.mutiny.subscription.MultiEmitter;

import java.util.Collections;

public class AGUISubscriber implements AgentSubscriber {

    private final MultiEmitter<? super BaseEvent> emitter;

    public AGUISubscriber(MultiEmitter<? super BaseEvent> emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onRunInitialized(AgentSubscriberParams params) {
        final RunStartedEvent event = new RunStartedEvent();
        event.setThreadId(params.input().threadId());
        event.setTimestamp(System.currentTimeMillis());
        emitter.emit(event);
    }

    @Override
    public void onRunFailed(AgentSubscriberParams params, Throwable error) {
        final RunErrorEvent event = new RunErrorEvent();
        event.setError(error.getMessage());
        event.setTimestamp(System.currentTimeMillis());
        emitter.emit(event);
    }

    @Override
    public void onRunFinalized(AgentSubscriberParams params) {
        final RunFinishedEvent event = new RunFinishedEvent();
        event.setThreadId(params.input().threadId());
        event.setTimestamp(System.currentTimeMillis());
        event.setResult(CollectionUtils.getFirst(params.messages()));
        emitter.emit(event);
    }

    @Override
    public void onEvent(BaseEvent event) {
    }

    @Override
    public void onRunStartedEvent(RunStartedEvent event) {
    }

    @Override
    public void onRunFinishedEvent(RunFinishedEvent event) {
    }

    @Override
    public void onRunErrorEvent(RunErrorEvent event) {
    }

    @Override
    public void onStepStartedEvent(StepStartedEvent event) {
    }

    @Override
    public void onStepFinishedEvent(StepFinishedEvent event) {
    }

    @Override
    public void onTextMessageStartEvent(TextMessageStartEvent event) {
    }

    @Override
    public void onTextMessageContentEvent(TextMessageContentEvent event) {
    }

    @Override
    public void onTextMessageEndEvent(TextMessageEndEvent event) {
    }

    @Override
    public void onToolCallStartEvent(ToolCallStartEvent event) {
    }

    @Override
    public void onToolCallArgsEvent(ToolCallArgsEvent event) {
    }

    @Override
    public void onToolCallEndEvent(ToolCallEndEvent event) {
    }

    @Override
    public void onToolCallResultEvent(ToolCallResultEvent event) {
    }

    @Override
    public void onStateSnapshotEvent(StateSnapshotEvent event) {
    }

    @Override
    public void onStateDeltaEvent(StateDeltaEvent event) {
    }

    @Override
    public void onMessagesSnapshotEvent(MessagesSnapshotEvent event) {
    }

    @Override
    public void onRawEvent(RawEvent event) {
    }

    @Override
    public void onCustomEvent(CustomEvent event) {
    }

    @Override
    public void onMessagesChanged(AgentSubscriberParams params) {
    }

    @Override
    public void onStateChanged(State state) {
    }

    @Override
    public void onNewMessage(BaseMessage message) {
    }

    @Override
    public void onNewToolCall(ToolCall toolCall) {
    }
}
