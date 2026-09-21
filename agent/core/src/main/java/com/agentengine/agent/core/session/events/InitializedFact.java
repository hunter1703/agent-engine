package com.agentengine.agent.core.session.events;

import com.agentengine.agent.core.session.state.SessionTopology;
import com.agentengine.util.context.UserContext;

public final class InitializedFact extends SessionFact {

  private SessionTopology topology;
  private UserContext ownerContext;

  public InitializedFact() {}

  public InitializedFact(final SessionTopology topology, final UserContext ownerContext) {
    this.topology = topology;
    this.ownerContext = ownerContext;
  }

  public SessionTopology getTopology() {
    return topology;
  }

  public void setTopology(final SessionTopology topology) {
    this.topology = topology;
  }

  public UserContext getOwnerContext() {
    return ownerContext;
  }

  public void setOwnerContext(final UserContext ownerContext) {
    this.ownerContext = ownerContext;
  }
}
