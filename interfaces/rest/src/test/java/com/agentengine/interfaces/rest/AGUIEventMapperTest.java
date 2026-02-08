package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.interfaces.rest.handlers.AGUIEventMapper;
import com.agui.core.event.BaseEvent;
import com.agui.core.type.EventType;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AGUIEventMapperTest {

  @Test
  void toolResultEndsStepBeforeAnswer() {
    final AGUIEventMapper mapper = new AGUIEventMapper("thread", "agent");

    final FunctionCall functionCall = FunctionCall.builder()
        .id("call-1")
        .name("run_cmd")
        .args(Map.of("command", "ls"))
        .build();
    final Event toolCallEvent = Event.builder()
        .id("event-1")
        .invocationId("run-1")
        .author("model")
        .content(Content.builder().role("model").parts(List.of(Part.builder().functionCall(functionCall).build()))
            .build())
        .partial(false)
        .build();

    final FunctionResponse functionResponse = FunctionResponse.builder()
        .id("call-1")
        .name("run_cmd")
        .response(Map.of("output", "ok"))
        .build();
    final Event toolResultEvent = Event.builder()
        .id("event-2")
        .invocationId("run-1")
        .author("model")
        .content(Content.builder().role("user").parts(List.of(
            Part.builder().functionResponse(functionResponse).build())).build())
        .partial(false)
        .build();

    final Event answerEvent = Event.builder()
        .id("event-3")
        .invocationId("run-1")
        .author("model")
        .content(Content.builder().role("model").parts(List.of(Part.builder().text("done").build())).build())
        .partial(false)
        .build();

    final List<BaseEvent> events = Flowable.just(toolCallEvent, toolResultEvent, answerEvent)
        .concatMap(mapper::map)
        .concatWith(Flowable.defer(mapper::onComplete))
        .toList()
        .blockingGet();

    assertThat(events).extracting(BaseEvent::getType).containsExactly(
        EventType.RUN_STARTED,
        EventType.STEP_STARTED,
        EventType.TOOL_CALL_START,
        EventType.TOOL_CALL_ARGS,
        EventType.TOOL_CALL_END,
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

    final Event userEvent = Event.builder()
        .id("event-1")
        .invocationId("run-1")
        .author("user")
        .content(Content.builder().role("user").parts(List.of(Part.builder().text("hi").build())).build())
        .partial(false)
        .build();
    final Event modelEvent = Event.builder()
        .id("event-2")
        .invocationId("run-1")
        .author("model")
        .content(Content.builder().role("model").parts(List.of(Part.builder().text("hello").build())).build())
        .partial(false)
        .build();

    final List<BaseEvent> events = Flowable.just(userEvent, modelEvent)
        .concatMap(mapper::map)
        .concatWith(Flowable.defer(mapper::onComplete))
        .toList()
        .blockingGet();

    assertThat(events).extracting(BaseEvent::getType)
        .containsExactly(
            EventType.RUN_STARTED,
            EventType.STEP_STARTED,
            EventType.TEXT_MESSAGE_START,
            EventType.TEXT_MESSAGE_CONTENT,
            EventType.TEXT_MESSAGE_END,
            EventType.TEXT_MESSAGE_START,
            EventType.TEXT_MESSAGE_CONTENT,
            EventType.TEXT_MESSAGE_END,
            EventType.STEP_FINISHED,
            EventType.RUN_FINISHED);
  }
}
