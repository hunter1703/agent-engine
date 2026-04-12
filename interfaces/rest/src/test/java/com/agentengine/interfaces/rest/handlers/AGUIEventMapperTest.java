package com.agentengine.interfaces.rest.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.core.agui.AGUIEventMapper;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.RunFinishedEvent;
import com.agui.core.event.RunStartedEvent;
import com.agui.core.event.StepFinishedEvent;
import com.agui.core.event.StepStartedEvent;
import com.agui.core.event.TextMessageChunkEvent;
import com.agui.core.event.TextMessageContentEvent;
import com.agui.core.event.TextMessageEndEvent;
import com.agui.core.event.TextMessageStartEvent;
import com.agui.core.event.ToolCallStartEvent;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class AGUIEventMapperTest {

    @Test
    public void shouldFinishStepWhenTurnCompleteIsSet() {
        final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
        final Event event = Event.builder()
                .turnComplete(true)
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.builder()
                                .functionCall(FunctionCall.builder()
                                        .name("search")
                                        .args(Map.of("query", "weather"))
                                        .build())
                                .build()))
                        .build())
                .build();

        final List<BaseEvent> mapped = mapper.map(event).toList().blockingGet();

        assertThat(mapped).anyMatch(StepStartedEvent.class::isInstance);
        assertThat(mapped).anyMatch(ToolCallStartEvent.class::isInstance);
        assertThat(mapped).anyMatch(StepFinishedEvent.class::isInstance);
    }

    @Test
    public void shouldNotFinishStepWhenToolCallArrivesWithoutTurnComplete() {
        final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
        final Event event = Event.builder()
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.builder()
                                .functionCall(FunctionCall.builder()
                                        .name("search")
                                        .args(Map.of("query", "weather"))
                                        .build())
                                .build()))
                        .build())
                .build();

        final List<BaseEvent> mapped = mapper.map(event).toList().blockingGet();

        assertThat(mapped).anyMatch(StepStartedEvent.class::isInstance);
        assertThat(mapped).anyMatch(ToolCallStartEvent.class::isInstance);
        assertThat(mapped).noneMatch(StepFinishedEvent.class::isInstance);
    }

    @Test
    public void shouldNotFinishStepForPartialTextChunks() {
        final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
        final Event event = Event.builder()
                .partial(true)
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText("hello")))
                        .build())
                .build();

        final List<BaseEvent> mapped = mapper.map(event).toList().blockingGet();

        assertThat(mapped).anyMatch(StepStartedEvent.class::isInstance);
        assertThat(mapped).anyMatch(TextMessageChunkEvent.class::isInstance);
        assertThat(mapped).noneMatch(StepFinishedEvent.class::isInstance);
    }

    @Test
    public void shouldNotFinishStepForCompletedTextWithoutTurnComplete() {
        final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
        final Event event = Event.builder()
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText("done")))
                        .build())
                .build();

        final List<BaseEvent> mapped = mapper.map(event).toList().blockingGet();

        assertThat(mapped).anyMatch(StepStartedEvent.class::isInstance);
        assertThat(mapped).anyMatch(TextMessageContentEvent.class::isInstance);
        assertThat(mapped).noneMatch(StepFinishedEvent.class::isInstance);
    }

    @Test
    public void shouldNotFinishStepForEndInvocationEventsWithoutTurnComplete() {
        final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
        final Event event = Event.builder()
                .actions(EventActions.builder().endInvocation(true).build())
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.builder()
                                .functionCall(FunctionCall.builder()
                                        .name("search")
                                        .args(Map.of("query", "weather"))
                                        .build())
                                .build()))
                        .build())
                .build();

        final List<BaseEvent> mapped = mapper.map(event).toList().blockingGet();

        assertThat(mapped).anyMatch(StepStartedEvent.class::isInstance);
        assertThat(mapped).noneMatch(StepFinishedEvent.class::isInstance);
    }

    @Test
    public void shouldEmitRunFinishedWhenFinishReasonIsPresent() {
        final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
        final Event event = Event.builder()
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText("done")))
                        .build())
                .build();

        final List<BaseEvent> mapped = mapper.map(Flowable.just(event)).toList().blockingGet();

        assertThat(mapped).anyMatch(RunFinishedEvent.class::isInstance);
    }

    @Test
    public void shouldPreserveDistinctRunIdsAcrossMultipleInvocationsInOneSessionHistoryReplay() {
        final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
        final Event first = Event.builder()
                .invocationId("run-1")
                .turnComplete(true)
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText("first")))
                        .build())
                .build();
        final Event second = Event.builder()
                .invocationId("run-2")
                .turnComplete(true)
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText("second")))
                        .build())
                .build();

        final List<BaseEvent> mapped =
                mapper.map(first).concatWith(mapper.map(second)).toList().blockingGet();
        final List<String> startedRunIds = mapped.stream()
                .filter(RunStartedEvent.class::isInstance)
                .map(RunStartedEvent.class::cast)
                .map(RunStartedEvent::getRunId)
                .toList();
        final List<String> finishedRunIds = mapped.stream()
                .filter(RunFinishedEvent.class::isInstance)
                .map(RunFinishedEvent.class::cast)
                .map(RunFinishedEvent::getRunId)
                .toList();

        assertThat(startedRunIds).containsExactly("run-1", "run-2");
        assertThat(finishedRunIds).containsExactly("run-1", "run-2");
    }

    @Test
    public void shouldReuseSourceEventTimestampWhenReplayingStoredEvents() {
        final long sourceTimestamp = 123_456_789L;
        final Event event = Event.builder()
                .invocationId("run-1")
                .timestamp(sourceTimestamp)
                .turnComplete(true)
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText("stable")))
                        .build())
                .build();

        final List<BaseEvent> mapped =
                new AGUIEventMapper("session-1", "agent-1").map(event).toList().blockingGet();

        assertThat(mapped).isNotEmpty();
        assertThat(mapped).extracting(BaseEvent::getTimestamp).containsOnly(sourceTimestamp);
    }

    @Test
    public void shouldIgnoreUserAuthoredInputEventsWhenReplayingSessionHistory() {
        final Event event = Event.builder()
                .invocationId("run-1")
                .author("user")
                .timestamp(123_456_789L)
                .turnComplete(true)
                .content(Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText("user-input")))
                        .build())
                .build();

        final List<BaseEvent> mapped =
                new AGUIEventMapper("session-1", "agent-1").map(event).toList().blockingGet();

        assertThat(mapped).isEmpty();
    }

    @Test
    public void shouldPreserveDeterministicStepAndMessageIdsAcrossReplayReads() {
        final Event event = Event.builder()
                .id("event-1")
                .invocationId("run-1")
                .timestamp(123_456_789L)
                .turnComplete(true)
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText("stable")))
                        .build())
                .build();

        final List<String> firstReplayIds = new AGUIEventMapper("session-1", "agent-1")
                .map(event).toList().blockingGet().stream()
                        .map(this::stableReplayIdentifier)
                        .filter(id -> id != null)
                        .toList();
        final List<String> secondReplayIds = new AGUIEventMapper("session-1", "agent-1")
                .map(event).toList().blockingGet().stream()
                        .map(this::stableReplayIdentifier)
                        .filter(id -> id != null)
                        .toList();

        assertThat(firstReplayIds)
                .isEqualTo(secondReplayIds)
                .contains("step-run-1-event-1", "msg-run-1-event-1", "run-1");
    }

    @Test
    public void shouldFallbackToSequentialStepAndMessageIdsWhenSourceEventIdIsMissing() {
        final Event event = Event.builder()
                .id(null)
                .invocationId("run-1")
                .timestamp(123_456_789L)
                .turnComplete(true)
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText("stable")))
                        .build())
                .build();

        final List<BaseEvent> first =
                new AGUIEventMapper("session-1", "agent-1").map(event).toList().blockingGet();
        final List<BaseEvent> second =
                new AGUIEventMapper("session-1", "agent-1").map(event).toList().blockingGet();

        final List<String> firstStepNames = extractStepNames(first);
        final List<String> secondStepNames = extractStepNames(second);
        final List<String> firstMessageIds = extractMessageIds(first);
        final List<String> secondMessageIds = extractMessageIds(second);

        assertThat(firstStepNames).isEqualTo(secondStepNames);
        assertThat(firstMessageIds).isEqualTo(secondMessageIds);
        assertThat(firstStepNames).allMatch(name -> name.startsWith("step-"));
        assertThat(firstMessageIds).allMatch(id -> id.startsWith("msg-"));
    }

    @Test
    public void shouldReuseOneMessageIdAcrossTextMessageEventsAndRepeatedMappings() {
        final Event event = Event.builder()
                .id("event-1")
                .invocationId("run-1")
                .timestamp(123_456_789L)
                .turnComplete(true)
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText("stable")))
                        .build())
                .build();

        final List<String> firstMessageIds = extractMessageIds(
                new AGUIEventMapper("session-1", "agent-1").map(event).toList().blockingGet());
        final List<String> secondMessageIds = extractMessageIds(
                new AGUIEventMapper("session-1", "agent-1").map(event).toList().blockingGet());

        assertThat(firstMessageIds).containsExactly("msg-run-1-event-1", "msg-run-1-event-1", "msg-run-1-event-1");
        assertThat(secondMessageIds).isEqualTo(firstMessageIds);
    }

    @Test
    public void shouldKeepMessageIdsDisjointBetweenRunsAndStableAcrossReads() {
        final Map<String, List<String>> firstRead = mapMessageIdsByRun();
        final Map<String, List<String>> secondRead = mapMessageIdsByRun();

        assertThat(firstRead.keySet()).containsExactly("run-1", "run-2");
        assertThat(firstRead.get("run-1")).isNotEmpty().allMatch(id -> id.equals("msg-run-1-event-1"));
        assertThat(firstRead.get("run-2")).isNotEmpty().allMatch(id -> id.equals("msg-run-2-event-2"));
        assertThat(secondRead)
                .withFailMessage(() -> "firstRead=" + firstRead + " secondRead=" + secondRead)
                .isEqualTo(firstRead);
    }

    @Test
    public void shouldKeepMessageIdSequencesStableWhenEventIdsAreMissing() {
        final Map<String, List<String>> firstRead = mapMessageIdsByRunWithMissingIds();
        final Map<String, List<String>> secondRead = mapMessageIdsByRunWithMissingIds();

        assertThat(firstRead).isEqualTo(secondRead);
        assertThat(firstRead.get("run-1")).allMatch(id -> id.startsWith("msg-"));
        assertThat(firstRead.get("run-2")).allMatch(id -> id.startsWith("msg-"));
    }

    private String stableReplayIdentifier(final BaseEvent event) {
        if (event instanceof RunStartedEvent runStartedEvent) {
            return runStartedEvent.getRunId();
        }
        if (event instanceof RunFinishedEvent runFinishedEvent) {
            return runFinishedEvent.getRunId();
        }
        if (event instanceof StepStartedEvent stepStartedEvent) {
            return stepStartedEvent.getStepName();
        }
        if (event instanceof StepFinishedEvent stepFinishedEvent) {
            return stepFinishedEvent.getStepName();
        }
        if (event instanceof TextMessageStartEvent textMessageStartEvent) {
            return textMessageStartEvent.getMessageId();
        }
        if (event instanceof TextMessageContentEvent textMessageContentEvent) {
            return textMessageContentEvent.getMessageId();
        }
        if (event instanceof TextMessageEndEvent textMessageEndEvent) {
            return textMessageEndEvent.getMessageId();
        }
        return null;
    }

    private List<String> extractStepNames(final List<BaseEvent> events) {
        return events.stream()
                .map(event -> {
                    if (event instanceof StepStartedEvent start) {
                        return start.getStepName();
                    }
                    if (event instanceof StepFinishedEvent finish) {
                        return finish.getStepName();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private List<String> extractMessageIds(final List<BaseEvent> events) {
        return events.stream()
                .map(event -> {
                    if (event instanceof TextMessageStartEvent start) {
                        return start.getMessageId();
                    }
                    if (event instanceof TextMessageContentEvent content) {
                        return content.getMessageId();
                    }
                    if (event instanceof TextMessageEndEvent end) {
                        return end.getMessageId();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, List<String>> mapMessageIdsByRun() {
        final Event first = Event.builder()
                .id("event-1")
                .invocationId("run-1")
                .turnComplete(true)
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText("first")))
                        .build())
                .build();
        final Event second = Event.builder()
                .id("event-2")
                .invocationId("run-2")
                .turnComplete(true)
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText("second")))
                        .build())
                .build();

        final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
        final List<BaseEvent> mapped =
                mapper.map(first).concatWith(mapper.map(second)).toList().blockingGet();

        final Map<String, List<String>> grouped = new LinkedHashMap<>();
        String currentRunId = null;
        for (final BaseEvent event : mapped) {
            if (event instanceof RunStartedEvent runStartedEvent) {
                currentRunId = runStartedEvent.getRunId();
                grouped.putIfAbsent(currentRunId, new ArrayList<>());
                continue;
            }
            final String messageId = extractMessageId(event);
            if (messageId != null && currentRunId != null) {
                grouped.get(currentRunId).add(messageId);
            }
        }
        return grouped;
    }

    private Map<String, List<String>> mapMessageIdsByRunWithMissingIds() {
        final Event first = Event.builder()
                .id(null)
                .invocationId("run-1")
                .turnComplete(true)
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText("alpha")))
                        .build())
                .build();
        final Event second = Event.builder()
                .id(null)
                .invocationId("run-2")
                .turnComplete(true)
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.fromText("beta")))
                        .build())
                .build();

        final AGUIEventMapper mapper = new AGUIEventMapper("session-1", "agent-1");
        final List<BaseEvent> mapped =
                mapper.map(first).concatWith(mapper.map(second)).toList().blockingGet();

        final Map<String, List<String>> grouped = new LinkedHashMap<>();
        String currentRunId = null;
        for (final BaseEvent event : mapped) {
            if (event instanceof RunStartedEvent runStartedEvent) {
                currentRunId = runStartedEvent.getRunId();
                grouped.putIfAbsent(currentRunId, new ArrayList<>());
                continue;
            }
            final String messageId = extractMessageId(event);
            if (messageId != null && currentRunId != null) {
                grouped.get(currentRunId).add(messageId);
            }
        }
        return grouped;
    }

    private String extractMessageId(final BaseEvent event) {
        if (event instanceof TextMessageStartEvent start) {
            return start.getMessageId();
        }
        if (event instanceof TextMessageContentEvent content) {
            return content.getMessageId();
        }
        if (event instanceof TextMessageEndEvent end) {
            return end.getMessageId();
        }
        return null;
    }
}
