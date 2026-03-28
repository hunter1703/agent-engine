package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.PekkoSerializable;

public interface BroadcasterFact extends PekkoSerializable {
  record PublishedFact(SequencedEvent<?> event) implements BroadcasterFact {
  }
}