package com.agentengine.agent.core.session.events;

import com.agentengine.agent.core.session.state.StartingChild;

public final class ChildStartingFact extends SessionFact {
  private StartingChild child;

  public ChildStartingFact() {}

  public ChildStartingFact(final StartingChild child) {
    this.child = child;
  }

  public StartingChild getChild() {
    return child;
  }

  public void setChild(final StartingChild child) {
    this.child = child;
  }
}
