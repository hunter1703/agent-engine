package com.agentengine.interfaces.rest.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.agui.core.event.BaseEvent;
import com.agui.core.event.RunFinishedEvent;
import com.agui.core.event.RunStartedEvent;
import com.agui.core.event.ToolCallResultEvent;
import com.agui.core.type.EventType;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AGUIEventEmitterTest {

  @Test
  void emitsRunLifecycleAndFinalAnswerResult() {
    List<BaseEvent> events = new ArrayList<>();
    AGUIEventEmitter emitter = new AGUIEventEmitter("thread-1", events::add);

    Event responseEvent = Event.builder().id("event-1").invocationId("run-1").author("model")
        .content(Content.builder().role("model").parts(Part.builder().text("done").build()).build()).build();

    emitter.onEvent(responseEvent);
    emitter.onComplete();

    RunStartedEvent started = events.stream().filter(item -> item instanceof RunStartedEvent)
        .map(RunStartedEvent.class::cast).findFirst().orElseThrow();
    assertThat(started.getType()).isEqualTo(EventType.RUN_STARTED);
    assertThat(started.getThreadId()).isEqualTo("thread-1");
    assertThat(started.getRunId()).isEqualTo("run-1");
    assertThat(started.getRawEvent()).isNull();

    RunFinishedEvent finished = events.stream().filter(item -> item instanceof RunFinishedEvent)
        .map(RunFinishedEvent.class::cast).findFirst().orElseThrow();
    assertThat(finished.getType()).isEqualTo(EventType.RUN_FINISHED);
    assertThat(finished.getResult()).isEqualTo("done");
  }

  @Test
  void emitsToolCallResultWithRole() {
    List<BaseEvent> events = new ArrayList<>();
    AGUIEventEmitter emitter = new AGUIEventEmitter("thread-1", events::add);

    Event responseEvent = Event.builder().id("event-1").invocationId("run-1").author("model")
        .content(Content.builder().role("model").parts(Part.builder()
            .functionResponse(
                FunctionResponse.builder().id("call-1").name("echo").response(Map.of("output", "ok")).build())
            .build()).build())
        .build();

    emitter.onEvent(responseEvent);

    ToolCallResultEvent resultEvent = events.stream().filter(item -> item instanceof ToolCallResultEvent)
        .map(ToolCallResultEvent.class::cast).findFirst().orElseThrow();
    assertThat(resultEvent.getType()).isEqualTo(EventType.TOOL_CALL_RESULT);
    assertThat(resultEvent.getToolCallId()).isEqualTo("call-1");
    assertThat(resultEvent.getContent()).isEqualTo("ok");
    assertThat(resultEvent.getRole()).isNotNull();
  }
}
