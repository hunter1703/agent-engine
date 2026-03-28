package com.agentengine.runtime.session.events;

import com.agentengine.runtime.session.state.SessionTopology;

public final class InitializedFact extends SessionFact {

  private SessionTopology topology;

  public InitializedFact() {
  }

  public InitializedFact(final SessionTopology topology) {
    this.topology = topology;
  }

  public SessionTopology getTopology() {
    return topology;
  }

  public void setTopology(final SessionTopology topology) {
    this.topology = topology;
  }
}
