package com.agentengine.util.agents.agui;

import com.agentengine.util.common.Violation;

public class CorrectionEvent extends BaseCustomEvent {
    private String message;

    public CorrectionEvent() {
        super("correction");
    }

    public CorrectionEvent(final Violation violation) {
        this();
        this.message = violation.message();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }
}
