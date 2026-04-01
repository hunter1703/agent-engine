package com.agentengine.util.agents.agui;

import com.agui.core.event.BaseEvent;
import com.agui.core.type.EventType;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Base class for all REASONING_* protocol events, carrying the shared messageId field. */
public abstract class BaseReasoningEvent extends BaseEvent {
    private String messageId;

    protected BaseReasoningEvent() {
        super(EventType.CUSTOM);
    }

    @Override
    @JsonIgnore
    public EventType getType() {
        return super.getType();
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(final String messageId) {
        this.messageId = messageId;
    }
}
