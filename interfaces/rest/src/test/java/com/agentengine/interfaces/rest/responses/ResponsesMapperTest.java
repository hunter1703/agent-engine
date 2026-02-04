package com.agentengine.interfaces.rest.responses;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.interfaces.rest.handlers.ResponsesMapper;
import com.agentengine.interfaces.rest.responses.dtos.BaseResponsesEventData;
import com.agentengine.interfaces.rest.responses.dtos.CreatedEventData;
import com.agentengine.interfaces.rest.responses.dtos.DoneEventData;
import com.agentengine.interfaces.rest.responses.dtos.ToolCallEventData;
import com.agentengine.interfaces.rest.responses.dtos.ToolCallResultEventData;
import com.agui.core.event.RunFinishedEvent;
import com.agui.core.event.RunStartedEvent;
import com.agui.core.event.StepStartedEvent;
import com.agui.core.event.TextMessageChunkEvent;
import com.agui.core.event.TextMessageContentEvent;
import com.agui.core.event.TextMessageEndEvent;
import com.agui.core.event.ToolCallArgsEvent;
import com.agui.core.event.ToolCallEndEvent;
import com.agui.core.event.ToolCallResultEvent;
import com.agui.core.event.ToolCallStartEvent;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResponsesMapperTest {

  @Test
  void mapsEventsAndAppendsDoneEvent() {
    final RunStartedEvent runStarted = new RunStartedEvent();
    runStarted.setRunId("run-1");
    runStarted.setRawEvent(Map.of("agentId", "agent-1"));

    final StepStartedEvent stepStarted = new StepStartedEvent();

    final TextMessageChunkEvent chunkEvent = new TextMessageChunkEvent();
    chunkEvent.setDelta("Hello");

    final TextMessageContentEvent contentEvent = new TextMessageContentEvent();
    contentEvent.setDelta("Hello");

    final TextMessageEndEvent endEvent = new TextMessageEndEvent();

    final RunFinishedEvent runFinished = new RunFinishedEvent();
    runFinished.setRunId("run-1");

    final ResponsesMapper mapper = new ResponsesMapper("fallback-agent");
    final List<BaseResponsesEventData> responses = Flowable.just(runStarted, stepStarted, chunkEvent, contentEvent,
            endEvent, runFinished)
        .concatMap(mapper::map)
        .concatWith(Flowable.defer(mapper::onComplete))
        .toList()
        .blockingGet();

    assertThat(responses).extracting(BaseResponsesEventData::getType).containsExactly(
        "response.created",
        "response.in_progress",
        "response.output_text.delta",
        "response.output_item.done",
        "response.completed",
        "response.done");

    final CreatedEventData createdEventData = (CreatedEventData) responses.get(0);
    assertThat(createdEventData.getResponse()).containsEntry("model", "agent-1");

    final DoneEventData doneEventData = (DoneEventData) responses.get(responses.size() - 1);
    assertThat(doneEventData.getResponse()).containsEntry("status", "completed");
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

    final ResponsesMapper mapper = new ResponsesMapper(null);
    final List<BaseResponsesEventData> responses = Flowable.just(startEvent, argsEvent, endEvent, resultEvent)
        .concatMap(mapper::map)
        .toList()
        .blockingGet();

    final ToolCallEventData toolCallEventData = (ToolCallEventData) responses.get(0);
    assertThat(toolCallEventData.getItem()).containsEntry("name", "weather")
        .containsEntry("arguments", "{\"city\":\"SF\"}");

    final ToolCallResultEventData toolCallResultEventData = (ToolCallResultEventData) responses.get(1);
    assertThat(toolCallResultEventData.getItem()).containsEntry("call_id", "call-1")
        .containsEntry("output", "{\"result\":\"ok\"}");
  }
}
