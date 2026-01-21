package com.agentengine.interfaces.rest.handlers;

import com.agui.core.event.BaseEvent;
import io.smallrye.mutiny.subscription.MultiEmitter;

public class SSESubscriber extends AbstractAgentSubscriber {
  public SSESubscriber(MultiEmitter<? super BaseEvent> emitter) {
    super(emitter::emit);
  }
}
