package com.agentengine.interfaces.rest.handlers;

import com.agui.core.agent.AgentSubscriber;
import com.agui.core.agent.AgentSubscriberParams;
import com.agui.core.event.*;
import com.agui.core.message.AssistantMessage;
import com.agui.core.message.BaseMessage;
import com.agui.core.state.State;
import com.agui.core.tool.ToolCall;
import org.jboss.logging.Logger;

import java.util.function.Consumer;

public abstract class AbstractAgentSubscriber implements AgentSubscriber {
    private static final Logger LOG = Logger.getLogger(AbstractAgentSubscriber.class);
    private final Consumer<? super BaseEvent> eventConsumer;

    public AbstractAgentSubscriber(Consumer<? super BaseEvent> eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    protected void emitEvent(BaseEvent event) {
        try {
            event.setTimestamp(System.currentTimeMillis());
            eventConsumer.accept(event);
        } catch (Exception e) {
            LOG.error("Error emitting event", e);
        }
    }

    @Override
    public void onRunInitialized(AgentSubscriberParams params) {
        final RunStartedEvent event = new RunStartedEvent();
        event.setThreadId(params.input().threadId());
        event.setRunId(params.input().runId());
        emitEvent(event);
    }

    @Override
    public void onRunFailed(AgentSubscriberParams params, Throwable error) {
        final RunErrorEvent event = new RunErrorEvent();
        event.setError(error.getMessage());
        emitEvent(event);
    }

    @Override
    public void onRunFinalized(AgentSubscriberParams params) {
        final RunFinishedEvent event = new RunFinishedEvent();
        event.setRunId(params.input().runId());
        event.setThreadId(params.input().threadId());
        emitEvent(event);
    }

    @Override
    public void onEvent(BaseEvent event) {
        emitEvent(event);
    }

    @Override
    public void onRunStartedEvent(RunStartedEvent event) {
        emitEvent(event);
    }

    @Override
    public void onRunFinishedEvent(RunFinishedEvent event) {
        emitEvent(event);
    }

    @Override
    public void onRunErrorEvent(RunErrorEvent event) {
        emitEvent(event);
    }

    @Override
    public void onStepStartedEvent(StepStartedEvent event) {
        emitEvent(event);
    }

    @Override
    public void onStepFinishedEvent(StepFinishedEvent event) {
        emitEvent(event);
    }

    @Override
    public void onTextMessageStartEvent(TextMessageStartEvent event) {
        emitEvent(event);
    }

    @Override
    public void onTextMessageContentEvent(TextMessageContentEvent event) {
        emitEvent(event);
    }

    @Override
    public void onTextMessageEndEvent(TextMessageEndEvent event) {
        emitEvent(event);
    }

    @Override
    public void onToolCallStartEvent(ToolCallStartEvent event) {
        emitEvent(event);
    }

    @Override
    public void onToolCallArgsEvent(ToolCallArgsEvent event) {
        emitEvent(event);
    }

    @Override
    public void onToolCallEndEvent(ToolCallEndEvent event) {
        emitEvent(event);
    }

    @Override
    public void onToolCallResultEvent(ToolCallResultEvent event) {
        emitEvent(event);
    }

    @Override
    public void onStateSnapshotEvent(StateSnapshotEvent event) {
        emitEvent(event);
    }

    @Override
    public void onStateDeltaEvent(StateDeltaEvent event) {
        emitEvent(event);
    }

    @Override
    public void onCustomEvent(CustomEvent event) {
        emitEvent(event);
    }

    @Override
    public void onStateChanged(State state) {
        // Emit a state delta event
        final StateDeltaEvent event = new StateDeltaEvent();
        event.setRawEvent(state);
        emitEvent(event);
    }

    @Override
    public void onNewMessage(BaseMessage message) {
        // Emit a text message content event for the new message
        final TextMessageContentEvent event = new TextMessageContentEvent();
        event.setDelta(message.getContent());
        event.setMessageId(message.getId());
        emitEvent(event);
    }

    @Override
    public void onNewToolCall(ToolCall toolCall) {
        // Emit a tool call start event for the new tool call
        final ToolCallStartEvent event = new ToolCallStartEvent();
        event.setToolCallId(toolCall.id());
        event.setToolCallName(toolCall.function().name());
        emitEvent(event);
    }
}