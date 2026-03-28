package com.agentengine.util.agents.agui;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.RunFinishedEvent;
import com.agui.core.event.RunStartedEvent;
import com.agui.core.event.StepFinishedEvent;
import com.agui.core.event.StepStartedEvent;
import com.agui.core.event.TextMessageChunkEvent;
import com.agui.core.event.TextMessageContentEvent;
import com.agui.core.event.TextMessageEndEvent;
import com.agui.core.event.TextMessageStartEvent;
import com.agui.core.event.ToolCallArgsEvent;
import com.agui.core.event.ToolCallEndEvent;
import com.agui.core.event.ToolCallResultEvent;
import com.agui.core.event.ToolCallStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class AGUIEventDecorator {

  private static final Logger LOG = LoggerFactory.getLogger(AGUIEventDecorator.class);

  private final AGUIMapperState state;

  public AGUIEventDecorator(final AGUIMapperState state) {
    this.state = state;
  }

  public <T extends BaseEvent> T decorate(final T event) {
    final long sourceTimestamp = state.currentSourceTimestamp();
    event.setTimestamp(sourceTimestamp > 0 ? sourceTimestamp : System.currentTimeMillis());
    @SuppressWarnings("unchecked")
    final Map<String, Object> rawEvent = CollectionUtils.nullSafeMutableMap((Map<String, Object>) event.getRawEvent());
    event.setRawEvent(null);
    rawEvent.putAll(JsonUtils.toMap(event));
    rawEvent.put("agentId", state.agentId());
    rawEvent.put("threadId", state.sessionId());
    event.setRawEvent(rawEvent);
    LOG.debug("Decorated event - eventType='{}', eventId='{}', agentId='{}'", event.getClass().getSimpleName(), eventId(event),
        state.agentId());
    return event;
  }

  private static String eventId(final BaseEvent event) {
    if (event instanceof RunStartedEvent runEvent) {
      return runEvent.getRunId();
    }
    if (event instanceof RunFinishedEvent runEvent) {
      return runEvent.getRunId();
    }
    if (event instanceof StepStartedEvent stepEvent) {
      return stepEvent.getStepName();
    }
    if (event instanceof StepFinishedEvent stepEvent) {
      return stepEvent.getStepName();
    }
    if (event instanceof TextMessageStartEvent messageEvent) {
      return messageEvent.getMessageId();
    }
    if (event instanceof TextMessageChunkEvent messageEvent) {
      return messageEvent.getMessageId();
    }
    if (event instanceof TextMessageContentEvent messageEvent) {
      return messageEvent.getMessageId();
    }
    if (event instanceof TextMessageEndEvent messageEvent) {
      return messageEvent.getMessageId();
    }
    if (event instanceof ReasoningStartEvent reasoningEvent) {
      return reasoningEvent.getMessageId();
    }
    if (event instanceof ReasoningEndEvent reasoningEvent) {
      return reasoningEvent.getMessageId();
    }
    if (event instanceof ReasoningMessageStartEvent reasoningEvent) {
      return reasoningEvent.getMessageId();
    }
    if (event instanceof ReasoningMessageContentEvent reasoningEvent) {
      return reasoningEvent.getMessageId();
    }
    if (event instanceof ReasoningMessageEndEvent reasoningEvent) {
      return reasoningEvent.getMessageId();
    }
    if (event instanceof ToolCallStartEvent toolEvent) {
      return toolEvent.getToolCallId();
    }
    if (event instanceof ToolCallArgsEvent toolEvent) {
      return toolEvent.getToolCallId();
    }
    if (event instanceof ToolCallEndEvent toolEvent) {
      return toolEvent.getToolCallId();
    }
    if (event instanceof ToolCallResultEvent toolEvent) {
      return toolEvent.getToolCallId();
    }
    return "unknown";
  }
}
