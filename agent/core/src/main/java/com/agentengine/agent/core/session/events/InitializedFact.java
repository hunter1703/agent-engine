package com.agentengine.agent.core.session.events;

import com.agentengine.agent.core.session.state.SessionTopology;
import com.agentengine.util.context.Principal;

public final class InitializedFact extends SessionFact {

  private SessionTopology topology;
  private Principal owner;

  public InitializedFact() {}

  public InitializedFact(final SessionTopology topology, final Principal owner) {
    this.topology = topology;
    this.owner = owner;
  }

  public SessionTopology getTopology() {
    return topology;
  }

  public void setTopology(final SessionTopology topology) {
    this.topology = topology;
  }

  public Principal getOwner() {
    return owner;
  }

  public void setOwner(final Principal owner) {
    this.owner = owner;
  }
}
