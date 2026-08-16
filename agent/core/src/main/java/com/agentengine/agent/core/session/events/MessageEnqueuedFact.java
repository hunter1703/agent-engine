package com.agentengine.agent.core.session.events;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.util.common.beans.UniqueRecord;

public final class MessageEnqueuedFact extends SessionFact {

  private UniqueRecord<UserMessage> message;

  public MessageEnqueuedFact() {}

  public MessageEnqueuedFact(final UniqueRecord<UserMessage> message) {
    this.message = message;
  }

  public UniqueRecord<UserMessage> getMessage() {
    return message;
  }

  public void setMessage(final UniqueRecord<UserMessage> message) {
    this.message = message;
  }
}
