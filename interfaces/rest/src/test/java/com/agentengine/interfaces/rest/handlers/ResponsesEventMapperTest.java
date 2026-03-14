package com.agentengine.interfaces.rest.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agui.AGUIEventMapper;
import com.agentengine.engine.api.agui.CorrectionEvent;
import com.agentengine.engine.api.agui.CorrectionMetadata;
import com.agentengine.interfaces.rest.dto.responses.ResponsesEvents;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.RunErrorEvent;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResponsesEventMapperTest {

  @Test
  void shouldMapReasoningTextAndCompletionEvents() {
    final Event first = Event.builder().id("evt-1").partial(true)
        .content(Content.builder().role("model").parts(List.of(Part.builder().thought(true).text("thinking ").build())).build()).build();
    final Event second = Event
        .builder().id("evt-2").turnComplete(true).finishReason(new FinishReason(FinishReason.Known.STOP)).content(Content.builder()
            .role("model").parts(List.of(Part.builder().thought(true).text("done").build(), Part.fromText("final answer"))).build())
        .build();

    final Flowable<BaseEvent> aguiEvents = new AGUIEventMapper("session-1", "agent-1").map(Flowable.just(first, second));
    final List<ResponsesEvents.StreamEvent> mapped = new ResponsesEventMapper("resp-1", "agent-1").map(aguiEvents).toList().blockingGet();

    assertThat(mapped).extracting(ResponsesEvents.StreamEvent::type).contains("response.reasoning_text.delta",
        "response.reasoning_text.done", "response.output_text.delta", "response.completed");

    final ResponsesEvents.CompletedEvent completed = mapped.stream().filter(ResponsesEvents.CompletedEvent.class::isInstance)
        .map(ResponsesEvents.CompletedEvent.class::cast).findFirst().orElseThrow();
    assertThat(completed.response().output()).isNotEmpty();
  }

  @Test
  void shouldMapCorrectionEventIntoReasoningSummaryEvents() {
    final CorrectionEvent correction = new CorrectionEvent(
        new CorrectionMetadata("guardrail", "partial_tool_calls", "Tool calls in partial"));

    final List<ResponsesEvents.StreamEvent> mapped = new ResponsesEventMapper("resp-2", "agent-2").map(Flowable.just(correction)).toList()
        .blockingGet();

    assertThat(mapped).extracting(ResponsesEvents.StreamEvent::type).contains("response.reasoning_summary_part.added",
        "response.reasoning_summary_text.delta", "response.reasoning_summary_text.done", "response.completed");
  }

  @Test
  void shouldMapToolLifecycleEvents() {
    final FunctionCall call = FunctionCall.builder().id("call-1").name("search").args(Map.of("query", "weather")).build();
    final FunctionResponse response = FunctionResponse.builder().id("call-1").name("search").response(Map.of("result", "sunny")).build();
    final Event event = Event.builder().id("evt-tools").turnComplete(true).finishReason(new FinishReason(FinishReason.Known.STOP))
        .content(Content.builder().role("model")
            .parts(List.of(Part.builder().functionCall(call).build(), Part.builder().functionResponse(response).build())).build())
        .build();

    final Flowable<BaseEvent> aguiEvents = new AGUIEventMapper("session-2", "agent-1").map(Flowable.just(event));
    final List<ResponsesEvents.StreamEvent> mapped = new ResponsesEventMapper("resp-3", "agent-1").map(aguiEvents).toList().blockingGet();

    assertThat(mapped).extracting(ResponsesEvents.StreamEvent::type).contains("response.output_item.added",
        "response.function_call_arguments.delta", "response.function_call_arguments.done");

    final ResponsesEvents.OutputItemAddedEvent callOutput = mapped.stream().filter(ResponsesEvents.OutputItemAddedEvent.class::isInstance)
        .map(ResponsesEvents.OutputItemAddedEvent.class::cast).filter(item -> "function_call_output".equals(item.item().get("type")))
        .findFirst().orElseThrow();

    assertThat(callOutput.item().get("call_id")).isEqualTo("call-1");
  }

  @Test
  void shouldEmitFailedEventWhenRunErrorIsMapped() {
    final RunErrorEvent errorEvent = new RunErrorEvent();
    errorEvent.setError("boom");

    final List<ResponsesEvents.StreamEvent> mapped = new ResponsesEventMapper("resp-4", "agent-3").map(Flowable.just(errorEvent)).toList()
        .blockingGet();

    assertThat(mapped).extracting(ResponsesEvents.StreamEvent::type).contains("response.failed");
  }
}
