package com.agentengine.agent.core.session.events;

import com.agentengine.agent.core.session.state.EnqueuedMessage;
import java.time.ZoneId;
import com.agentengine.util.common.beans.UniqueRecord;

public final class StartedFact extends SessionFact {
  private UniqueRecord<EnqueuedMessage> message;
  private ZoneId timeZone;

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

  /** The time zone of the user the message was sent as, when the run was accepted. */
  public ZoneId getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(final ZoneId timeZone) {
    this.timeZone = timeZone;
  }
}
