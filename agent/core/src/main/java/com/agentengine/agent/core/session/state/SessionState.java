package com.agentengine.agent.core.session.state;

import com.agentengine.util.pekko.PekkoSerializable;

/** Minimal session lifecycle for a session actor. */
public enum SessionState implements PekkoSerializable {
  IDLE(true),
  TRIGGERED_RUN(false),
  RUNNING(false),
  CONTINUING(false),
  PAUSED(true),
  /** Deleted: refuses every command, and is never run or recovered again. */
  DELETED(true);

  private final boolean terminal;

  SessionState(final boolean terminal) {
    this.terminal = terminal;
  }

  public boolean isTerminal() {
    return terminal;
  }
}
