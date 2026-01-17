package com.agentengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.AgentListener;
import com.agentengine.engine.events.AgentEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AgentSseTest {

  @Test
  void eventsStreamEmitsListenerEvents() throws Exception {
    AgentService service = mock(AgentService.class);
    AgentEngine engine = mock(AgentEngine.class);
    when(service.getOrStartEngine(eq("agent"), eq("config.json"))).thenReturn(engine);

    AtomicReference<AgentListener> listenerRef = new AtomicReference<>();
    doAnswer(
            invocation -> {
              listenerRef.set(invocation.getArgument(0));
              return null;
            })
        .when(engine)
        .registerListener(any());

    AgentRestAPI resource = new AgentRestAPI(service);
    Multi<AgentEvent> stream = resource.events("agent", "config.json", "session");

    LinkedBlockingQueue<AgentEvent> queue = new LinkedBlockingQueue<>();
    Cancellable cancellable = stream.subscribe().with(queue::add);

    AgentEvent sessionEvent = queue.poll(1, TimeUnit.SECONDS);
    assertThat(sessionEvent).isNotNull();
    assertThat(sessionEvent.event()).isEqualTo("session");

    listenerRef.get().onReasoningStart("session");
    AgentEvent event = queue.poll(1, TimeUnit.SECONDS);
    assertThat(event).isNotNull();
    assertThat(event.event()).isEqualTo("reasoning_start");

    cancellable.cancel();
  }
}
