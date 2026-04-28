package com.agentengine.agent.core.session.state;

import com.agentengine.util.pekko.PekkoSerializable;

/** Minimal session lifecycle for a session actor. */
public enum SessionState implements PekkoSerializable {
    IDLE(true),
    TRIGGERED_RUN(false),
    RUNNING(false),
    RESUMING(false),
    PAUSED(true);

    private final boolean terminal;

    SessionState(final boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
