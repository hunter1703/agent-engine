package com.agentengine.interfaces.rest.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.core.agui.AGUIEventMapper;
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
        final Event first = Event.builder()
                .id("evt-1")
                .partial(true)
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(
                                Part.builder().thought(true).text("thinking ").build()))
                        .build())
                .build();
        final Event second = Event.builder()
                .id("evt-2")
                .turnComplete(true)
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(
                                Part.builder().thought(true).text("done").build(), Part.fromText("final answer")))
                        .build())
                .build();

        final Flowable<BaseEvent> aguiEvents =
                new AGUIEventMapper("session-1", "agent-1").map(Flowable.just(first, second));
        final ResponsesEventMapper mapper = new ResponsesEventMapper("resp-1", "agent-1", 1L);

        final List<ResponsesEventMapper.ResponseOutputEvent> mapped =
                aguiEvents.concatMap(mapper::mapEvent).toList().blockingGet();

        assertThat(mapped)
                .extracting(ResponsesEventMapper.ResponseOutputEvent::type)
                .contains("response.reasoning.delta", "response.output_text.delta", "response.completed");
    }

    @Test
    void shouldMapToolLifecycleEvents() {
        final FunctionCall call = FunctionCall.builder()
                .id("call-1")
                .name("search")
                .args(Map.of("query", "weather"))
                .build();
        final FunctionResponse response = FunctionResponse.builder()
                .id("call-1")
                .name("search")
                .response(Map.of("result", "sunny"))
                .build();
        final Event event = Event.builder()
                .id("evt-tools")
                .turnComplete(true)
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(
                                Part.builder().functionCall(call).build(),
                                Part.builder().functionResponse(response).build()))
                        .build())
                .build();

        final Flowable<BaseEvent> aguiEvents = new AGUIEventMapper("session-2", "agent-1").map(Flowable.just(event));
        final ResponsesEventMapper mapper = new ResponsesEventMapper("resp-3", "agent-1", 1L);

        final List<ResponsesEventMapper.ResponseOutputEvent> mapped =
                aguiEvents.concatMap(mapper::mapEvent).toList().blockingGet();

        assertThat(mapped)
                .extracting(ResponsesEventMapper.ResponseOutputEvent::type)
                .contains(
                        "response.function_call.output_item.added",
                        "response.function_call.arguments.delta",
                        "response.function_call.done",
                        "response.tool_call.output_item.added");

        final ResponsesEventMapper.ResponseOutputEvent functionCallAdded = mapped.stream()
                .filter(output -> "response.function_call.output_item.added".equals(output.type()))
                .findFirst()
                .orElseThrow();
        assertThat(functionCallAdded.toolCallId()).isEqualTo("call-1");
        assertThat(functionCallAdded.toolName()).isEqualTo("search");
    }

    @Test
    void shouldEmitErrorWhenRunErrorIsMapped() {
        final RunErrorEvent errorEvent = new RunErrorEvent();
        errorEvent.setError("boom");
        final ResponsesEventMapper mapper = new ResponsesEventMapper("resp-4", "agent-3", 1L);

        assertThatThrownBy(() -> mapper.mapEvent(errorEvent).toList().blockingGet())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom");
    }
}
