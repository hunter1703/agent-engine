package com.agentengine.util.agents.agui;

import com.agui.core.event.CustomEvent;

public abstract class BaseCustomEvent extends CustomEvent {
    private final String name;

    protected BaseCustomEvent(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
