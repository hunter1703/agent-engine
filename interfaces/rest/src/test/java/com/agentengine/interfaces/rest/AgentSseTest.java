package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.StreamAguiEventsRequestHandler;
import com.agentengine.interfaces.rest.support.HandlerInstance;
import com.agui.core.event.BaseEvent;
import com.agui.core.type.EventType;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.enterprise.inject.Instance;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

class AgentSseTest {

  @Test
  void eventsStreamEmitsListenerEvents() {
    AgentExecutionService executionService = mock(AgentExecutionService.class);

    // Create an event that contains content to trigger all expected events
    Event answerEvent =
        Event.builder()
            .id("event-1")
            .invocationId("run-1")
            .author("model")
            .content(
                Content.builder().role("model").parts(Part.builder().text("hello").build()).build())
            .partial(false).turnComplete(true)
            .build();

    when(executionService.run(any(AgentRequest.class)))
        .thenReturn(Flowable.just(answerEvent));

    AgentRestAPI resource = new AgentRestAPI(buildHandlers(executionService), null);
    AgentRequest agentRequest = new AgentRequest();
    agentRequest.setAgentId("agent");
    agentRequest.setAgentConfigPath("config.json");
    agentRequest.setSessionId("session");
    agentRequest.setMessage("hello");
    agentRequest.setType(RequestType.STREAM_AGUI_EVENTS.name());
    var publisher = resource.events(agentRequest);
    assertThat(publisher).isNotNull();

    // Collect events directly from the publisher using the reactive streams
    // approach
    final var events =
        CompletableFuture.supplyAsync(
                () -> {
                  final var collectedEvents = new ArrayList<BaseEvent>();
                  final var latch = new CountDownLatch(1);

                  publisher.subscribe(
                          new Subscriber<>() {
                              @Override
                              public void onSubscribe(Subscription s) {
                                  s.request(Long.MAX_VALUE); // Request all events
                              }

                              @Override
                              public void onNext(BaseEvent event) {
                                  collectedEvents.add(event);
                              }

                              @Override
                              public void onError(Throwable t) {
                                  latch.countDown();
                              }

                              @Override
                              public void onComplete() {
                                  latch.countDown();
                              }
                          });

                  try {
                    latch.await(5, TimeUnit.SECONDS); // Wait up to 5 seconds for completion
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }

                  return collectedEvents;
                })
            .join();

    assertThat(events)
        .extracting(BaseEvent::getType)
        .containsExactly(
            EventType.RUN_STARTED,
            EventType.STEP_STARTED,
            EventType.TEXT_MESSAGE_START,
            EventType.TEXT_MESSAGE_CONTENT,
            EventType.TEXT_MESSAGE_END,
            EventType.STEP_FINISHED,
            EventType.RUN_FINISHED);
  }

  @Test
  void eventsStreamGeneratesSessionIdIfMissing() {
    AgentExecutionService executionService = mock(AgentExecutionService.class);

    Event event =
        Event.builder()
            .id("event-1")
            .invocationId("run-1")
            .author("model")
            .content(
                Content.builder().role("model").parts(Part.builder().text("hello").build()).build())
            .partial(false)
            .build();

    when(executionService.run(any(AgentRequest.class))).thenReturn(Flowable.just(event));

    AgentRestAPI resource = new AgentRestAPI(buildHandlers(executionService), null);
    AgentRequest agentRequest = new AgentRequest();
    agentRequest.setAgentId("agent");
    agentRequest.setAgentConfigPath("config.json");
    // Session ID is intentionally missing
    agentRequest.setMessage("hello");
    agentRequest.setType(RequestType.STREAM_AGUI_EVENTS.name());

    var publisher = resource.events(agentRequest);

    assertThat(publisher).isNotNull();
    assertThat(agentRequest.getSessionId()).isNotNull();
  }

  private static Instance<AgentRequestHandler<?>> buildHandlers(
      final AgentExecutionService executionService) {
    final StreamAguiEventsRequestHandler streamingHandler =
        new StreamAguiEventsRequestHandler(executionService);
    return new HandlerInstance(List.of(streamingHandler));
  }
}
