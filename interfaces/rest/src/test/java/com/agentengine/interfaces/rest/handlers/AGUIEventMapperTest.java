package com.agentengine.interfaces.rest.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.agui.core.event.BaseEvent;
import com.agui.core.event.CustomEvent;
import com.agui.core.event.RunFinishedEvent;
import com.agui.core.event.RunStartedEvent;
import com.agui.core.event.TextMessageContentEvent;
import com.agui.core.event.ThinkingTextMessageContentEvent;
import com.agui.core.event.ToolCallResultEvent;
import com.agui.core.type.EventType;
import com.agentengine.engine.api.utils.CorrectionUtils;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AGUIEventMapperTest {

  @Test
  void emitsRunLifecycleAndFinalAnswerResult() {
    Event responseEvent =
        Event.builder()
            .id("event-1")
            .invocationId("run-1")
            .author("model")
            .content(
                Content.builder().role("model").parts(Part.builder().text("done").build()).build())
            .build();

    AGUIEventMapper mapper = new AGUIEventMapper("thread-1", "agent-1");
    List<BaseEvent> events =
        Flowable.just(responseEvent)
            .map(mapper::map)
            .concatMap(eventStream -> eventStream)
            .concatWith(Flowable.defer(mapper::onComplete))
            .toList()
            .blockingGet();

    RunStartedEvent started =
        events.stream()
            .filter(item -> item instanceof RunStartedEvent)
            .map(RunStartedEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(started.getType()).isEqualTo(EventType.RUN_STARTED);
    assertThat(started.getThreadId()).isEqualTo("thread-1");
    assertThat(started.getRunId()).isEqualTo("run-1");
    assertThat(started.getRawEvent()).isNotNull();

    RunFinishedEvent finished =
        events.stream()
            .filter(item -> item instanceof RunFinishedEvent)
            .map(RunFinishedEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(finished.getType()).isEqualTo(EventType.RUN_FINISHED);
    assertThat(finished.getResult()).isEqualTo("done");
  }

  @Test
  void emitsToolCallResultWithRole() {
    Event responseEvent =
        Event.builder()
            .id("event-1")
            .invocationId("run-1")
            .author("model")
            .content(
                Content.builder()
                    .role("model")
                    .parts(
                        Part.builder()
                            .functionResponse(
                                FunctionResponse.builder()
                                    .id("call-1")
                                    .name("echo")
                                    .response(Map.of("output", "ok"))
                                    .build())
                            .build())
                    .build())
            .build();

    AGUIEventMapper mapper = new AGUIEventMapper("thread-1", "agent-1");
    List<BaseEvent> events =
        Flowable.just(responseEvent)
            .map(mapper::map)
            .concatMap(eventStream -> eventStream)
            .toList()
            .blockingGet();

    ToolCallResultEvent resultEvent =
        events.stream()
            .filter(item -> item instanceof ToolCallResultEvent)
            .map(ToolCallResultEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(resultEvent.getType()).isEqualTo(EventType.TOOL_CALL_RESULT);
    assertThat(resultEvent.getToolCallId()).isEqualTo("call-1");
    assertThat(resultEvent.getContent()).isEqualTo("{\"output\":\"ok\"}");
    assertThat(resultEvent.getRole()).isNotNull();
  }

  @Test
  void toolResultEndsStepBeforeAnswer() {
    final AGUIEventMapper mapper = new AGUIEventMapper("thread", "agent");

    final FunctionCall functionCall =
        FunctionCall.builder().id("call-1").name("run_cmd").args(Map.of("command", "ls")).build();
    final Event toolCallEvent =
        Event.builder()
            .id("event-1")
            .invocationId("run-1")
            .author("model")
            .content(
                Content.builder()
                    .role("model")
                    .parts(Part.builder().functionCall(functionCall).build())
                    .build())
            .partial(false)
            .build();

    final FunctionResponse functionResponse =
        FunctionResponse.builder()
            .id("call-1")
            .name("run_cmd")
            .response(Map.of("output", "ok"))
            .build();
    final Event toolResultEvent =
        Event.builder()
            .id("event-2")
            .invocationId("run-1")
            .author("model")
            .content(
                Content.builder()
                    .role("user")
                    .parts(Part.builder().functionResponse(functionResponse).build())
                    .build())
            .partial(false)
            .build();

    final Event answerEvent =
        Event.builder()
            .id("event-3")
            .invocationId("run-1")
            .author("model")
            .content(
                Content.builder()
                    .role("model")
                    .parts(Part.builder().text("done").build())
                    .build())
            .partial(false)
            .build();

    final List<BaseEvent> events =
        Flowable.just(toolCallEvent, toolResultEvent, answerEvent)
            .concatMap(e -> mapper.map(e).concatWith(Flowable.empty())) // map returns Flowable
            .concatWith(Flowable.defer(mapper::onComplete))
            .toList()
            .blockingGet();

    assertThat(events)
        .extracting(BaseEvent::getType)
        .containsExactly(
            EventType.RUN_STARTED,
            EventType.STEP_STARTED,
            EventType.TOOL_CALL_START,
            EventType.TOOL_CALL_ARGS,
            EventType.TOOL_CALL_END,
            EventType.STEP_FINISHED,
            EventType.STEP_STARTED,
            EventType.TOOL_CALL_RESULT,
            EventType.STEP_FINISHED,
            EventType.STEP_STARTED,
            EventType.TEXT_MESSAGE_START,
            EventType.TEXT_MESSAGE_CONTENT,
            EventType.TEXT_MESSAGE_END,
            EventType.STEP_FINISHED,
            EventType.RUN_FINISHED);
  }

  @Test
  void userTextDoesNotFinishStep() {
    final AGUIEventMapper mapper = new AGUIEventMapper("thread", "agent");

    final Event userEvent =
        Event.builder()
            .id("event-1")
            .invocationId("run-1")
            .author("user")
            .content(
                Content.builder()
                    .role("user")
                    .parts(List.of(Part.builder().text("hi").build()))
                    .build())
            .partial(false)
            .build();
    final Event modelEvent =
        Event.builder()
            .id("event-2")
            .invocationId("run-1")
            .author("model")
            .content(
                Content.builder()
                    .role("model")
                    .parts(List.of(Part.builder().text("hello").build()))
                    .build())
            .partial(false)
            .build();

    final List<BaseEvent> events =
        Flowable.just(userEvent, modelEvent)
            .concatMap(e -> mapper.map(e).concatWith(Flowable.empty()))
            .concatWith(Flowable.defer(mapper::onComplete))
            .toList()
            .blockingGet();

    assertThat(events)
        .extracting(BaseEvent::getType)
        .containsExactly(
            EventType.RUN_STARTED,
            EventType.STEP_STARTED,
            EventType.TEXT_MESSAGE_START,
            EventType.TEXT_MESSAGE_CONTENT,
            EventType.TEXT_MESSAGE_END,
            EventType.STEP_FINISHED,
            EventType.STEP_STARTED,
            EventType.TEXT_MESSAGE_START,
            EventType.TEXT_MESSAGE_CONTENT,
            EventType.TEXT_MESSAGE_END,
            EventType.STEP_FINISHED,
            EventType.RUN_FINISHED);
  }

  @Test
  void emitsThinkingCyclesWithChunks() {
    final Event thinkingEvent =
        Event.builder()
            .id("event-1")
            .invocationId("run-1")
            .author("model")
            .content(
                Content.builder()
                    .role("model")
                    .parts(List.of(Part.builder().text("Thinking...").thought(true).build()))
                    .build())
            .partial(true)
            .build();

    final AGUIEventMapper mapper = new AGUIEventMapper("thread-1", "agent-1");
    final List<BaseEvent> events =
        Flowable.just(thinkingEvent)
            .concatMap(e -> mapper.map(e).concatWith(Flowable.empty()))
            .concatWith(Flowable.defer(mapper::onComplete))
            .toList()
            .blockingGet();

    assertThat(events)
        .extracting(BaseEvent::getType)
        .containsExactly(
            EventType.RUN_STARTED,
            EventType.STEP_STARTED,
            EventType.THINKING_START,
            EventType.THINKING_TEXT_MESSAGE_START,
            EventType.THINKING_TEXT_MESSAGE_CONTENT,
            EventType.THINKING_TEXT_MESSAGE_END,
            EventType.THINKING_END,
            EventType.STEP_FINISHED,
            EventType.RUN_FINISHED);

    final Optional<ThinkingTextMessageContentEvent> contentEvent =
        events.stream()
            .filter(event -> event instanceof ThinkingTextMessageContentEvent)
            .map(ThinkingTextMessageContentEvent.class::cast)
            .findFirst();
    assertThat(contentEvent).isPresent();
    @SuppressWarnings("unchecked")
    final Map<String, Object> rawEvent = (Map<String, Object>) contentEvent.get().getRawEvent();
    assertThat(rawEvent).containsEntry("chunk", true).containsEntry("delta", "Thinking...");
  }

  @Test
  void flushesChunkedTextOnComplete() {
    final Event firstChunk = Event.builder()
        .id("event-1")
        .invocationId("run-1")
        .author("model")
        .content(Content.builder().role("model").parts(List.of(Part.builder().text("Hello ").build())).build())
        .partial(true)
        .build();
    final Event secondChunk = Event.builder()
        .id("event-2")
        .invocationId("run-1")
        .author("model")
        .content(Content.builder().role("model").parts(List.of(Part.builder().text("world").build())).build())
        .partial(true)
        .build();

    final AGUIEventMapper mapper = new AGUIEventMapper("thread-1", "agent-1");
    final List<BaseEvent> events =
        Flowable.just(firstChunk, secondChunk)
            .concatMap(mapper::map)
            .concatWith(Flowable.defer(mapper::onComplete))
            .toList()
            .blockingGet();

    assertThat(events)
        .extracting(BaseEvent::getType)
        .containsExactly(
            EventType.RUN_STARTED,
            EventType.STEP_STARTED,
            EventType.TEXT_MESSAGE_START,
            EventType.TEXT_MESSAGE_CHUNK,
            EventType.TEXT_MESSAGE_CHUNK,
            EventType.TEXT_MESSAGE_CONTENT,
            EventType.TEXT_MESSAGE_END,
            EventType.STEP_FINISHED,
            EventType.RUN_FINISHED);

    final TextMessageContentEvent contentEvent =
        events.stream()
            .filter(event -> event instanceof TextMessageContentEvent)
            .map(TextMessageContentEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(contentEvent.getDelta()).isEqualTo("Hello world");

    final RunFinishedEvent finished =
        events.stream()
            .filter(event -> event instanceof RunFinishedEvent)
            .map(RunFinishedEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(finished.getResult()).isEqualTo("Hello world");
  }

  @Test
  void mapsCorrectionEventsToCustomEvents() {
    final EventActions actions =
        CorrectionUtils.buildCorrectionActions("violation", "v1", "Fix it");
    final Event correctionEvent =
        Event.builder()
            .id("event-1")
            .invocationId("run-1")
            .author("user")
            .actions(actions)
            .content(Content.builder().role("user").parts(Part.builder().text("Fix it").build()).build())
            .build();

    final AGUIEventMapper mapper = new AGUIEventMapper("thread-1", "agent-1");
    final List<BaseEvent> events =
        Flowable.just(correctionEvent)
            .concatMap(e -> mapper.map(e).concatWith(Flowable.empty()))
            .toList()
            .blockingGet();

    assertThat(events)
        .extracting(BaseEvent::getType)
        .containsExactly(EventType.RUN_STARTED, EventType.CUSTOM);

    final CustomEvent customEvent =
        events.stream()
            .filter(event -> event instanceof CustomEvent)
            .map(CustomEvent.class::cast)
            .findFirst()
            .orElseThrow();
    @SuppressWarnings("unchecked")
    final Map<String, Object> rawEvent = (Map<String, Object>) customEvent.getRawEvent();
    assertThat(rawEvent)
        .containsEntry("name", "CORRECTION")
        .containsEntry("code", "v1")
        .containsEntry("message", "Fix it");
  }

  @Test
  void handlesComplexThinkingToTextTransition() {
    final AGUIEventMapper mapper = new AGUIEventMapper("thread-1", "agent-1");

    final Event thinkingEvent = Event.builder()
        .id("e1").invocationId("r1").author("model")
        .content(Content.builder().parts(List.of(Part.builder().text("Searching...").thought(true).build())).build())
        .partial(true).build();

    final Event answerEvent = Event.builder()
        .id("e2").invocationId("r1").author("model")
        .content(Content.builder().parts(List.of(Part.builder().text("Found it: result").build())).build())
        .partial(false).build();

    final List<BaseEvent> events = Flowable.just(thinkingEvent, answerEvent)
        .concatMap(mapper::map)
        .concatWith(Flowable.defer(mapper::onComplete))
        .toList().blockingGet();

    assertThat(events).extracting(BaseEvent::getType).containsExactly(
        EventType.RUN_STARTED,
        EventType.STEP_STARTED,
        EventType.THINKING_START,
        EventType.THINKING_TEXT_MESSAGE_START,
        EventType.THINKING_TEXT_MESSAGE_CONTENT,
        EventType.THINKING_TEXT_MESSAGE_END,
        EventType.THINKING_END,
        EventType.TEXT_MESSAGE_START,
        EventType.TEXT_MESSAGE_CONTENT,
        EventType.TEXT_MESSAGE_END,
        EventType.STEP_FINISHED,
        EventType.RUN_FINISHED
    );
  }

  @Test
  void handlesToolCallWithinStep() {
    final AGUIEventMapper mapper = new AGUIEventMapper("thread-1", "agent-1");

    final FunctionCall call = FunctionCall.builder().id("c1").name("tool").args(Map.of("q", "x")).build();
    final Event callEvent = Event.builder()
        .id("e1").invocationId("r1").author("model")
        .content(Content.builder().parts(List.of(Part.builder().functionCall(call).build())).build())
        .partial(false).build();

    final FunctionResponse resp = FunctionResponse.builder().id("c1").name("tool").response(Map.of("res", "ok")).build();
    final Event respEvent = Event.builder()
        .id("e2").invocationId("r1").author("user")
        .content(Content.builder().parts(List.of(Part.builder().functionResponse(resp).build())).build())
        .build();

    final List<BaseEvent> events = Flowable.just(callEvent, respEvent)
        .concatMap(mapper::map)
        .concatWith(Flowable.defer(mapper::onComplete))
        .toList().blockingGet();

    assertThat(events).extracting(BaseEvent::getType).containsExactly(
        EventType.RUN_STARTED,
        EventType.STEP_STARTED,
        EventType.TOOL_CALL_START,
        EventType.TOOL_CALL_ARGS,
        EventType.TOOL_CALL_END,
        EventType.STEP_FINISHED,
        EventType.STEP_STARTED,
        EventType.TOOL_CALL_RESULT,
        EventType.STEP_FINISHED,
        EventType.RUN_FINISHED
    );
  }
}
