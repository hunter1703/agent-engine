package com.agentengine.interfaces.rest.responses;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agui.core.event.*;
import com.agentengine.interfaces.rest.responses.dtos.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Maps AGUI events to Responses API format for Codex CLI compatibility
 */
public class ResponsesApiMapper implements Consumer<BaseEvent> {
    private final Map<String, ToolCallDetails> toolCallDetails = new HashMap<>();
    private final Consumer<? super BaseResponsesEventData> eventConsumer;
    private int thinkingIndex = 0;
    private int contentIndex = 0;
    private int toolCallIndex = 0;
    private boolean thinkingStarted = false;

    public ResponsesApiMapper(final Consumer<? super BaseResponsesEventData> eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void accept(final BaseEvent baseEvent) {
        final BaseResponsesEventData responsesEventData = switch (baseEvent) {
            case RunStartedEvent runStartedEvent ->
                    new CreatedEventData(runStartedEvent.getRunId(), getValueFromRawEvent(runStartedEvent, "agentId"));
            case RunFinishedEvent runFinishedEvent -> new CompletedEventData(runFinishedEvent.getRunId());
            case RunErrorEvent runErrorEvent ->
                    new FailedEventData(UUID.randomUUID().toString(), runErrorEvent.getError());
            case StepStartedEvent _ -> new InProgressEventData();
            case ThinkingStartEvent _ -> {
                thinkingStarted = true;
                yield new ReasoningSummaryPartAddedEventData(thinkingIndex);
            }
            case ThinkingEndEvent _ -> {
                thinkingIndex++;
                thinkingStarted = false;
                yield null;
            }
            case TextMessageStartEvent _ -> {
                thinkingStarted = false;
                yield null;
            }
            case TextMessageEndEvent _ -> {
                thinkingStarted = false;
                contentIndex++;
                yield null;
            }
            case TextMessageChunkEvent textMessageChunkEvent -> {
                final String delta = textMessageChunkEvent.getDelta();
                if (thinkingStarted) {
                    yield new ReasoningSummaryTextDeltaEventData(delta, thinkingIndex);
                } else {
                    yield new OutputTextDeltaEventData(delta, contentIndex);
                }
            }
            case TextMessageContentEvent textMessageContentEvent -> {
                final String delta = textMessageContentEvent.getDelta();
                if (thinkingStarted) {
                    yield new ReasoningSummaryTextDeltaEventData(delta, thinkingIndex);
                } else {
                    yield new MessageCompletedEventData(delta, contentIndex);
                }
            }
            case ToolCallStartEvent toolCallStartEvent -> {
                toolCallDetails.put(toolCallStartEvent.getToolCallId(), new ToolCallDetails(toolCallStartEvent.getToolCallName(), new StringBuilder(), toolCallIndex++));
                yield null;
            }
            case ToolCallArgsEvent toolCallArgsEvent -> {
                final String toolCallId = toolCallArgsEvent.getToolCallId();
                final ToolCallDetails details = toolCallDetails.get(toolCallId);
                if (details != null) {
                    details.argsBuilder().append(toolCallArgsEvent.getDelta());
                }
                yield null;
            }
            case ToolCallEndEvent toolCallEndEvent -> {
                final String toolCallId = toolCallEndEvent.getToolCallId();
                ToolCallDetails details = toolCallDetails.get(toolCallId);
                if (details == null) {
                    yield null;
                }
                String arguments = details.argsBuilder().toString();
                arguments = StringUtils.isBlank(arguments) ? "{}" : arguments;

                String toolName = details.name();
                if (StringUtils.isNotBlank(toolName)) {
                    toolName = "unknown";
                }
                yield new ToolCallEventData(toolCallId, toolName, arguments, details.index());
            }
            case ToolCallResultEvent toolCallResultEvent -> {
                final String toolCallId = toolCallResultEvent.getToolCallId();
                final ToolCallDetails details = toolCallDetails.get(toolCallId);
                if (details == null) {
                    yield null;
                }
                yield new ToolCallResultEventData(toolCallId, toolCallResultEvent.getContent(), details.index());
            }
            default -> throw new IllegalStateException(STR."Unexpected value: \{baseEvent}");
        };
        if (responsesEventData != null) {
            eventConsumer.accept(responsesEventData);
        }
    }

    private static <T> T getValueFromRawEvent(final BaseEvent event, final String key) {
        if (event == null) {
            return null;
        }
        //noinspection unchecked
        final Map<String, Object> rawEvent = (Map<String, Object>) event.getRawEvent();
        return CollectionUtils.getValueFromMap(rawEvent, key);
    }

    private record ToolCallDetails(String name, StringBuilder argsBuilder, int index) {
    }
}