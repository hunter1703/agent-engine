package com.agentengine.interfaces.rest.responses;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.interfaces.rest.handlers.ResponsesEventMapper;
import com.agentengine.interfaces.rest.responses.dtos.BaseResponsesEventData;
import com.agentengine.interfaces.rest.responses.dtos.CreatedEventData;
import com.agentengine.interfaces.rest.responses.dtos.DoneEventData;
import com.agentengine.interfaces.rest.responses.dtos.OutputItemAddedEventData;
import com.agentengine.interfaces.rest.responses.dtos.OutputItemDoneEventData;
import com.agentengine.interfaces.rest.responses.dtos.ToolCallEventData;
import com.agentengine.interfaces.rest.responses.dtos.ToolCallResultEventData;
import com.agui.core.event.RunFinishedEvent;
import com.agui.core.event.RunStartedEvent;
import com.agui.core.event.StepStartedEvent;
import com.agui.core.event.TextMessageChunkEvent;
import com.agui.core.event.TextMessageContentEvent;
import com.agui.core.event.TextMessageEndEvent;
import com.agui.core.event.TextMessageStartEvent;
import com.agui.core.event.ThinkingEndEvent;
import com.agui.core.event.ThinkingStartEvent;
import com.agui.core.event.ToolCallArgsEvent;
import com.agui.core.event.ToolCallEndEvent;
import com.agui.core.event.ToolCallResultEvent;
import com.agui.core.event.ToolCallStartEvent;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResponsesEventMapperTest {

  @Test
  void mapsEventsAndAppendsDoneEvent() {
    final RunStartedEvent runStarted = new RunStartedEvent();
    runStarted.setRunId("run-1");
    runStarted.setRawEvent(Map.of("agentId", "agent-1"));

    final StepStartedEvent stepStarted = new StepStartedEvent();

    final TextMessageStartEvent startEvent = new TextMessageStartEvent();

    final TextMessageChunkEvent chunkEvent = new TextMessageChunkEvent();
    chunkEvent.setDelta("Hello");

    final TextMessageContentEvent contentEvent = new TextMessageContentEvent();
    contentEvent.setDelta("Hello");

    final TextMessageEndEvent endEvent = new TextMessageEndEvent();

    final RunFinishedEvent runFinished = new RunFinishedEvent();
    runFinished.setRunId("run-1");

    final ResponsesEventMapper mapper = new ResponsesEventMapper("fallback-agent");
    final List<BaseResponsesEventData> responses =
        Flowable.just(
                runStarted,
                stepStarted,
                startEvent,
                chunkEvent,
                contentEvent,
                endEvent,
                runFinished)
            .concatMap(mapper::map)
            .concatWith(Flowable.defer(mapper::onComplete))
            .toList()
            .blockingGet();

    assertThat(responses)
        .extracting(BaseResponsesEventData::getType)
        .containsExactly(
            "response.created",
            "response.in_progress",
            "response.output_item.added",
            "response.output_text.delta",
            "response.completed",
            "response.output_item.done",
            "response.done");

    final CreatedEventData createdEventData = (CreatedEventData) responses.get(0);
    assertThat(createdEventData.getResponse()).containsEntry("model", "agent-1");

    final DoneEventData doneEventData = (DoneEventData) responses.get(responses.size() - 1);
    assertThat(doneEventData.getResponse()).containsEntry("status", "completed");

    final OutputItemAddedEventData messageAdded = (OutputItemAddedEventData) responses.get(2);
    final OutputItemDoneEventData messageDone = (OutputItemDoneEventData) responses.get(5);
    assertThat(messageAdded.getItem()).containsEntry("type", "message");
    assertThat(messageDone.getItem()).containsEntry("type", "message");
    assertThat(messageDone.getItem()).containsEntry("role", "assistant");
    assertThat(messageDone.getItem())
        .containsEntry("content", List.of(Map.of("type", "output_text", "text", "Hello")));
  }

  @Test
  void mapsToolCallEventsWithNamesAndArgs() {
    final ToolCallStartEvent startEvent = new ToolCallStartEvent();
    startEvent.setToolCallId("call-1");
    startEvent.setToolCallName("weather");

    final ToolCallArgsEvent argsEvent = new ToolCallArgsEvent();
    argsEvent.setToolCallId("call-1");
    argsEvent.setDelta("{\"city\":\"SF\"}");

    final ToolCallEndEvent endEvent = new ToolCallEndEvent();
    endEvent.setToolCallId("call-1");

    final ToolCallResultEvent resultEvent = new ToolCallResultEvent();
    resultEvent.setToolCallId("call-1");
    resultEvent.setContent("{\"result\":\"ok\"}");

    final ResponsesEventMapper mapper = new ResponsesEventMapper(null);
    final List<BaseResponsesEventData> responses =
        Flowable.just(startEvent, argsEvent, endEvent, resultEvent)
            .concatMap(mapper::map)
            .toList()
            .blockingGet();

    final OutputItemAddedEventData toolCallAddedEventData =
        (OutputItemAddedEventData) responses.get(0);
    assertThat(toolCallAddedEventData.getItem())
        .containsEntry("name", "weather")
        .containsEntry("arguments", "")
        .containsEntry("call_id", "call-1");

    final ToolCallEventData toolCallEventData = (ToolCallEventData) responses.get(1);
    assertThat(toolCallEventData.getItem())
        .containsEntry("name", "weather")
        .containsEntry("arguments", "{\"city\":\"SF\"}");

    final ToolCallResultEventData toolCallResultEventData =
        (ToolCallResultEventData) responses.get(2);
    assertThat(toolCallResultEventData.getItem())
        .containsEntry("call_id", "call-1")
        .containsEntry("output", "{\"result\":\"ok\"}");

    final OutputItemDoneEventData toolCallResultDoneEventData =
        (OutputItemDoneEventData) responses.get(3);
    assertThat(toolCallResultDoneEventData.getItem())
        .containsEntry("call_id", "call-1")
        .containsEntry("output", "{\"result\":\"ok\"}");
  }

  @Test
  void closesMessageBeforeToolCall() {
    final TextMessageStartEvent startEvent = new TextMessageStartEvent();

    final TextMessageChunkEvent chunkEvent = new TextMessageChunkEvent();
    chunkEvent.setDelta("Hello");

    final ToolCallStartEvent toolCallStartEvent = new ToolCallStartEvent();
    toolCallStartEvent.setToolCallId("call-1");
    toolCallStartEvent.setToolCallName("weather");

    final ResponsesEventMapper mapper = new ResponsesEventMapper(null);
    final List<BaseResponsesEventData> responses =
        Flowable.just(startEvent, chunkEvent, toolCallStartEvent)
            .concatMap(mapper::map)
            .toList()
            .blockingGet();

    assertThat(responses)
        .extracting(BaseResponsesEventData::getType)
        .containsExactly(
            "response.output_item.added",
            "response.output_text.delta",
            "response.output_item.done",
            "response.output_item.added");

    final OutputItemDoneEventData messageDone = (OutputItemDoneEventData) responses.get(2);
    assertThat(messageDone.getItem()).containsEntry("type", "message");
    assertThat(messageDone.getItem()).containsEntry("role", "assistant");
    assertThat(messageDone.getItem())
        .containsEntry("content", List.of(Map.of("type", "output_text", "text", "Hello")));
  }

  @Test
  void mapsThinkingEventsToReasoningSummary() {
    final ThinkingStartEvent thinkingStartEvent = new ThinkingStartEvent();

    final TextMessageChunkEvent chunkEvent = new TextMessageChunkEvent();
    chunkEvent.setDelta("Think about it");

    final ThinkingEndEvent thinkingEndEvent = new ThinkingEndEvent();

    final ResponsesEventMapper mapper = new ResponsesEventMapper(null);
    final List<BaseResponsesEventData> responses =
        Flowable.just(thinkingStartEvent, chunkEvent, thinkingEndEvent)
            .concatMap(mapper::map)
            .toList()
            .blockingGet();

    assertThat(responses)
        .extracting(BaseResponsesEventData::getType)
        .containsExactly(
            "response.output_item.added",
            "response.reasoning_summary_part.added",
            "response.reasoning_summary_text.delta",
            "response.output_item.done");

    final OutputItemAddedEventData reasoningAdded = (OutputItemAddedEventData) responses.get(0);
    assertThat(reasoningAdded.getItem()).containsEntry("type", "reasoning");

    final BaseResponsesEventData reasoningDone = responses.get(3);
    assertThat(reasoningDone.getType()).isEqualTo("response.output_item.done");
  }
}
