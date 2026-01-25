package com.agentengine.interfaces.rest.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.agui.core.event.BaseEvent;
import com.agui.core.event.RunFinishedEvent;
import com.agui.core.event.RunStartedEvent;
import com.agui.core.event.ToolCallResultEvent;
import com.agui.core.type.EventType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AGUIEventEmitterTest {

  @Test
  void emitsRunLifecycleAndFinalAnswerResult() {
    List<BaseEvent> events = new ArrayList<>();
    AGUIEventEmitter emitter = new AGUIEventEmitter("thread-1", "hello", events::add);

    emitter.onRunStarted("thread-1", "run-1");
    emitter.onTextMessageStart("thread-1", "msg-1", "assistant");
    emitter.onTextMessageDelta("thread-1", "msg-1", "done");
    emitter.onTextMessageEnd("thread-1", "msg-1");
    emitter.onRunFinished("thread-1", "run-1");

    RunStartedEvent started = (RunStartedEvent) events.getFirst();
    assertThat(started.getType()).isEqualTo(EventType.RUN_STARTED);
    assertThat(started.getThreadId()).isEqualTo("thread-1");
    assertThat(started.getRunId()).isEqualTo("run-1");
    assertThat(started.getRawEvent()).isEqualTo(Map.of("input", Map.of("message", "hello")));

    RunFinishedEvent finished = (RunFinishedEvent) events.stream().filter(event -> event instanceof RunFinishedEvent)
        .map(RunFinishedEvent.class::cast).findFirst().orElseThrow();
    assertThat(finished.getType()).isEqualTo(EventType.RUN_FINISHED);
    assertThat(finished.getResult()).isEqualTo("done");
  }

  @Test
  void emitsToolCallResultWithRole() {
    List<BaseEvent> events = new ArrayList<>();
    AGUIEventEmitter emitter = new AGUIEventEmitter("thread-1", "hello", events::add);

    emitter.onToolCallResult("thread-1", "call-1", "ok");

    assertThat(events).hasSize(1);
    ToolCallResultEvent event = (ToolCallResultEvent) events.getFirst();
    assertThat(event.getType()).isEqualTo(EventType.TOOL_CALL_RESULT);
    assertThat(event.getToolCallId()).isEqualTo("call-1");
    assertThat(event.getContent()).isEqualTo("ok");
    assertThat(event.getRole()).isNotNull();
  }
}
