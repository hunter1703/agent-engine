package com.agentengine.agent.core.session.events;

import com.agentengine.agent.core.session.state.EnqueuedMessage;
import com.agentengine.util.common.beans.UniqueRecord;

public final class StartedFact extends SessionFact {
  private UniqueRecord<EnqueuedMessage> message;

  public StartedFact() {}

  public StartedFact(final UniqueRecord<EnqueuedMessage> message) {
    this.message = message;
  }

  public UniqueRecord<EnqueuedMessage> getMessage() {
    return message;
  }

  public void setMessage(final UniqueRecord<EnqueuedMessage> message) {
    this.message = message;
  }
}
