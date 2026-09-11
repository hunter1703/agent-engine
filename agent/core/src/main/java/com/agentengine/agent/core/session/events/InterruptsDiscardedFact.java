package com.agentengine.agent.core.session.events;

import java.util.Set;

public final class InterruptsDiscardedFact extends SessionFact {

  private Set<String> interruptIds;

  public InterruptsDiscardedFact() {}

  public InterruptsDiscardedFact(final Set<String> interruptIds) {
    this.interruptIds = interruptIds;
  }

  public Set<String> getInterruptIds() {
    return interruptIds;
  }

  public void setInterruptIds(final Set<String> interruptIds) {
    this.interruptIds = interruptIds;
  }
}
