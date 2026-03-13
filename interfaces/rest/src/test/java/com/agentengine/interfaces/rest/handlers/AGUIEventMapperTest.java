package com.agentengine.interfaces.rest.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agui.AGUIEventMapper;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.RunFinishedEvent;
import com.agui.core.event.StepFinishedEvent;
import com.agui.core.event.StepStartedEvent;
import com.agui.core.event.TextMessageChunkEvent;
import com.agui.core.event.TextMessageContentEvent;
import com.agui.core.event.ToolCallStartEvent;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AGUIEventMapperTest {

  @Test
  void shouldFinishStepWhenTurnCompleteIsSet() {
    final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
    final Event event = Event.builder().turnComplete(true).content(Content.builder().role("model")
        .parts(List.of(Part.builder().functionCall(FunctionCall.builder().name("search").args(Map.of("query", "weather")).build()).build()))
        .build()).build();

    final List<BaseEvent> mapped = mapper.map(event).toList().blockingGet();

    assertThat(mapped).anyMatch(StepStartedEvent.class::isInstance);
    assertThat(mapped).anyMatch(ToolCallStartEvent.class::isInstance);
    assertThat(mapped).anyMatch(StepFinishedEvent.class::isInstance);
  }

  @Test
  void shouldNotFinishStepWhenToolCallArrivesWithoutTurnComplete() {
    final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
    final Event event = Event.builder().content(Content.builder().role("model")
        .parts(List.of(Part.builder().functionCall(FunctionCall.builder().name("search").args(Map.of("query", "weather")).build()).build()))
        .build()).build();

    final List<BaseEvent> mapped = mapper.map(event).toList().blockingGet();

    assertThat(mapped).anyMatch(StepStartedEvent.class::isInstance);
    assertThat(mapped).anyMatch(ToolCallStartEvent.class::isInstance);
    assertThat(mapped).noneMatch(StepFinishedEvent.class::isInstance);
  }

  @Test
  void shouldNotFinishStepForPartialTextChunks() {
    final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
    final Event event = Event.builder().partial(true)
        .content(Content.builder().role("model").parts(List.of(Part.fromText("hello"))).build()).build();

    final List<BaseEvent> mapped = mapper.map(event).toList().blockingGet();

    assertThat(mapped).anyMatch(StepStartedEvent.class::isInstance);
    assertThat(mapped).anyMatch(TextMessageChunkEvent.class::isInstance);
    assertThat(mapped).noneMatch(StepFinishedEvent.class::isInstance);
  }

  @Test
  void shouldNotFinishStepForCompletedTextWithoutTurnComplete() {
    final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
    final Event event = Event.builder().content(Content.builder().role("model").parts(List.of(Part.fromText("done"))).build()).build();

    final List<BaseEvent> mapped = mapper.map(event).toList().blockingGet();

    assertThat(mapped).anyMatch(StepStartedEvent.class::isInstance);
    assertThat(mapped).anyMatch(TextMessageContentEvent.class::isInstance);
    assertThat(mapped).noneMatch(StepFinishedEvent.class::isInstance);
  }

  @Test
  void shouldNotFinishStepForEndInvocationEventsWithoutTurnComplete() {
    final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
    final Event event = Event.builder().actions(EventActions.builder().endInvocation(true).build()).content(Content.builder().role("model")
        .parts(List.of(Part.builder().functionCall(FunctionCall.builder().name("search").args(Map.of("query", "weather")).build()).build()))
        .build()).build();

    final List<BaseEvent> mapped = mapper.map(event).toList().blockingGet();

    assertThat(mapped).anyMatch(StepStartedEvent.class::isInstance);
    assertThat(mapped).noneMatch(StepFinishedEvent.class::isInstance);
  }

  @Test
  void shouldEmitRunFinishedWhenFinishReasonIsPresent() {
    final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
    final Event event = Event.builder().finishReason(new FinishReason(FinishReason.Known.STOP))
        .content(Content.builder().role("model").parts(List.of(Part.fromText("done"))).build()).build();

    final List<BaseEvent> mapped = mapper.map(event).toList().blockingGet();

    assertThat(mapped).anyMatch(RunFinishedEvent.class::isInstance);
  }

  @Test
  void shouldNotEmitRunFinishedOnCompleteWithoutFinishReason() {
    final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
    final Event event = Event.builder().content(Content.builder().role("model").parts(List.of(Part.fromText("not done"))).build()).build();
    mapper.map(event).toList().blockingGet();

    final List<BaseEvent> completionEvents = mapper.onComplete().toList().blockingGet();

    assertThat(completionEvents).noneMatch(RunFinishedEvent.class::isInstance);
  }

  @Test
  void shouldNotEmitDuplicateRunFinishedOnCompleteAfterFinishReason() {
    final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
    final Event event = Event.builder().finishReason(new FinishReason(FinishReason.Known.STOP))
        .content(Content.builder().role("model").parts(List.of(Part.fromText("done"))).build()).build();
    mapper.map(event).toList().blockingGet();

    final List<BaseEvent> completionEvents = mapper.onComplete().toList().blockingGet();

    assertThat(completionEvents).noneMatch(RunFinishedEvent.class::isInstance);
  }
}
