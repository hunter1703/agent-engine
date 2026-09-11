package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.Copyable;
import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.pekko.PekkoSerializable;

public sealed interface BroadcasterFact extends PekkoSerializable {
  record PublishedFact(SequencedEvent<Copyable<?>> event) implements BroadcasterFact {}
}
