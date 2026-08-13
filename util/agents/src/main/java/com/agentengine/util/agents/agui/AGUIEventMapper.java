package com.agentengine.util.agents.agui;

import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.*;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.beans.FileDetails;
import com.agui.community.core.event.*;
import com.agui.community.core.interrupt.SuccessOutcome;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps runtime SessionEvent to AGUI events */
public final class AGUIEventMapper implements EventMapper<SessionEvent, Event> {
    private static final Logger LOG = LoggerFactory.getLogger(AGUIEventMapper.class);

    public enum Mode {
        LIVE,
        REPLAY
    }

    private final AGUIMapperState state;
    private final AGUITextMapper textMapper;
    private final AGUIToolCallMapper toolCallMapper;

    public AGUIEventMapper(final String sessionId, final String agentId, final Mode mode) {
        this.state = new AGUIMapperState(sessionId, agentId);
        this.textMapper = new AGUITextMapper(state, mode);
        this.toolCallMapper = new AGUIToolCallMapper(state);
    }

    @Override
    public Flowable<Event> map(final SessionEvent event) {
        return mapInternal(event).map(this::withAuthor);
    }

    @Override
    public Flowable<Event> onError(final Throwable throwable) {
        LOG.debug("Processing error mapping - throwable={}", ExceptionUtils.getErrorMessage(throwable));
        final RunErrorEvent errorEvent =
                new RunErrorEvent(ExceptionUtils.getErrorMessage(throwable), null, state.timestamp(), null);
        LOG.debug("Generated output event in onError - eventType=RunErrorEvent");
        return Flowable.just(errorEvent);
    }

    private Flowable<Event> mapInternal(final SessionEvent event) {
        // Scope all state lookups below to this event's source session before anything else runs
        // — including the early-return branches, so a stale error/live-marker event never reads
        // or mutates the wrong session's in-flight run/step/message state.
        state.recordSourceEvent(event);
        LOG.debug(
                "Mapping SessionEvent - id={}, sessionId={}, runId={}, author={}, turnComplete={}, finishReason={}",
                event.getId(),
                event.getSessionId(),
                event.getRunId(),
                event.getAuthor(),
                event.getTurnComplete(),
                event.getFinishReason());

        if (event.isLiveMarker()) {
            textMapper.switchToLiveMode();
            return Flowable.empty();
        }

        if (event.isError()) {
            LOG.debug(
                    "Mapping error event for session={}, errorMessage={}",
                    event.getSessionId(),
                    event.getErrorMessage());
            final RunErrorEvent errorEvent = new RunErrorEvent(event.getErrorMessage(), null, state.timestamp(), null);
            return textMapper.finalizeOpenContent().concatWith(Flowable.just(errorEvent));
        }

        Flowable<Event> eventFlow = Flowable.empty();
        if (state.hasNewRun(event.getRunId())) {
            eventFlow = eventFlow.concatWith(startRun(event.getRunId()));
        }
        return eventFlow.concatWith(mapEventInternal(event)).concatWith(finishRunIfNeeded(event));
    }

    private Flowable<Event> mapEventInternal(final SessionEvent event) {
        if (SessionEventUtils.isInternal(event)) {
            return Flowable.empty();
        }
        Flowable<Event> flowable = startStepIfNeeded();
        if (SessionEventUtils.isCorrectionEvent(event)) {
            return flowable.concatWith(mapCorrectionEvent(event)).concatWith(finishStepIfNeeded(event));
        }

        final Optional<Content> content = Optional.ofNullable(event.getContent());
        if (content.isPresent()) {
            final boolean partial = Boolean.TRUE.equals(event.isPartial());
            for (final Part part : content.get().parts().orElse(List.of())) {
                flowable = flowable.concatWith(mapPart(part, partial));
            }
        }

        // Emit attachment events for user messages that carried file metadata.
        // Attachments are stored in event metadata (keyed by SessionEventUtils.ATTACHMENTS)
        // rather than as fileData Parts, so they survive the single-text LLM message constraint.
        if (Constants.AUTHOR_USER.equals(event.getAuthor())) {
            final List<FileDetails> attachments =
                    CollectionUtils.getValueFromMap(event.getMetadata(), SessionEventUtils.ATTACHMENTS);
            for (final FileDetails fileDetails : CollectionUtils.nullSafeList(attachments)) {
                flowable = flowable.concatWith(textMapper.mapAttachment(fileDetails));
            }
        }

        return flowable.concatWith(finishStepIfNeeded(event));
    }

    private Flowable<Event> mapPart(final Part part, final boolean partial) {
        if (part.thought().orElse(false)) {
            return textMapper.mapThought(part.text().orElse(""), partial);
        }

        Flowable<Event> flowable = Flowable.empty();
        final String text = part.text().orElse(null);

        if (text != null) {
            flowable = flowable.concatWith(textMapper.mapText(text, partial));
        }

        final FunctionCall call = part.functionCall().orElse(null);
        if (call != null) {
            flowable = flowable.concatWith(textMapper.closeReasoningIfNeeded())
                    .concatWith(toolCallMapper.mapToolCall(call));
        }

        final FunctionResponse response = part.functionResponse().orElse(null);
        if (response != null) {
            flowable = flowable.concatWith(textMapper.closeReasoningIfNeeded())
                    .concatWith(toolCallMapper.mapToolResponse(response));
        }
        return flowable;
    }

    private Flowable<Event> startRun(final String runId) {
        state.startRun(runId);
        final RunStartedEvent event =
                new RunStartedEvent(state.sessionId(), state.currentRunId(), null, null, state.timestamp(), null);
        LOG.debug("Generated output event - eventType=RunStartedEvent, runId={}", event.threadId());
        return Flowable.just(event);
    }

    private Flowable<Event> finishRunIfNeeded(final SessionEvent event) {
        if (event.getFinishReason() == null) {
            return Flowable.empty();
        }

        final String finishedRunId = state.finishRun();
        if (finishedRunId == null) {
            // No run was ever started for this event's session — nothing to close out.
            return Flowable.empty();
        }
        final RunFinishedEvent finishedEvent = new RunFinishedEvent(
                state.sessionId(), finishedRunId, new SuccessOutcome(), null, state.timestamp(), null);
        LOG.debug("Generated output event - eventType=RunFinishedEvent, runId={}", finishedEvent.threadId());
        return Flowable.just(finishedEvent);
    }

    private Flowable<Event> startStepIfNeeded() {
        if (state.hasStartedStep()) {
            return Flowable.empty();
        }

        final StepStartedEvent stepEvent = new StepStartedEvent(state.startNextStep(), state.timestamp(), null);
        LOG.debug("Generated output event - eventType=StepStartedEvent, stepName={}", stepEvent.stepName());
        return Flowable.just(stepEvent);
    }

    private Flowable<Event> finishStepIfNeeded(final SessionEvent event) {
        if (!Boolean.TRUE.equals(event.isTurnComplete()) || !state.hasStartedStep()) {
            return Flowable.empty();
        }
        return finishStep();
    }

    private Flowable<Event> finishStep() {
        final StepFinishedEvent event = new StepFinishedEvent(state.finishStep(), state.timestamp(), null);
        LOG.debug("Generated output event - eventType=StepFinishedEvent, stepName={}", event.stepName());
        return textMapper.finalizeOpenContent().concatWith(Flowable.just(event));
    }

    private Flowable<Event> mapCorrectionEvent(final SessionEvent event) {
        final Violation violation = Objects.requireNonNull(
                CollectionUtils.getValueFromMap(event.getMetadata(), SessionEventUtils.VIOLATION));
        LOG.debug("Generated correction event - correctionMetadataPresent=true");
        return Flowable.just(AGUIUtils.buildCorrectionEvent(violation, state.timestamp()));
    }

    /**
     * Stamps the id of whoever actually generated this event onto every emitted event's {@code
     * rawEvent} — the source event's own author (an agent's name, or {@code "user"}), not the root
     * agent the client invoked — merged alongside whatever that event already carries there rather
     * than overwriting it. A single stage at the end of the pipeline, instead of threading {@code
     * author} through every {@code new XxxEvent(...)} call site across the mapper.
     */
    private Event withAuthor(final Event event) {
        //noinspection unchecked
        final Map<String, Object> rawEvent = CollectionUtils.nullSafeMutableMap((Map<String, Object>) event.rawEvent());
        rawEvent.put("author", state.currentAuthor());
        return switch (event) {
            case RunStartedEvent runStartedEvent ->
                new RunStartedEvent(
                        runStartedEvent.threadId(),
                        runStartedEvent.runId(),
                        runStartedEvent.parentRunId(),
                        runStartedEvent.input(),
                        runStartedEvent.timestamp(),
                        rawEvent);
            case RunFinishedEvent runFinishedEvent ->
                new RunFinishedEvent(
                        runFinishedEvent.threadId(),
                        runFinishedEvent.runId(),
                        runFinishedEvent.outcome(),
                        runFinishedEvent.result(),
                        runFinishedEvent.timestamp(),
                        rawEvent);
            case RunErrorEvent runErrorEvent ->
                new RunErrorEvent(runErrorEvent.message(), runErrorEvent.code(), runErrorEvent.timestamp(), rawEvent);
            case StepStartedEvent stepStartedEvent ->
                new StepStartedEvent(stepStartedEvent.stepName(), stepStartedEvent.timestamp(), rawEvent);
            case StepFinishedEvent stepFinishedEvent ->
                new StepFinishedEvent(stepFinishedEvent.stepName(), stepFinishedEvent.timestamp(), rawEvent);
            case TextMessageStartEvent textMessageStartEvent ->
                new TextMessageStartEvent(
                        textMessageStartEvent.messageId(),
                        textMessageStartEvent.role(),
                        textMessageStartEvent.timestamp(),
                        rawEvent);
            case TextMessageContentEvent textMessageContentEvent ->
                new TextMessageContentEvent(
                        textMessageContentEvent.messageId(),
                        textMessageContentEvent.delta(),
                        textMessageContentEvent.timestamp(),
                        rawEvent);
            case TextMessageEndEvent textMessageEndEvent ->
                new TextMessageEndEvent(textMessageEndEvent.messageId(), textMessageEndEvent.timestamp(), rawEvent);
            case TextMessageChunkEvent textMessageChunkEvent ->
                new TextMessageChunkEvent(
                        textMessageChunkEvent.messageId(),
                        textMessageChunkEvent.role(),
                        textMessageChunkEvent.delta(),
                        textMessageChunkEvent.timestamp(),
                        rawEvent);
            case ToolCallStartEvent toolCallStartEvent ->
                new ToolCallStartEvent(
                        toolCallStartEvent.toolCallId(),
                        toolCallStartEvent.toolCallName(),
                        toolCallStartEvent.parentMessageId(),
                        toolCallStartEvent.timestamp(),
                        rawEvent);
            case ToolCallArgsEvent toolCallArgsEvent ->
                new ToolCallArgsEvent(
                        toolCallArgsEvent.toolCallId(),
                        toolCallArgsEvent.delta(),
                        toolCallArgsEvent.timestamp(),
                        rawEvent);
            case ToolCallEndEvent toolCallEndEvent ->
                new ToolCallEndEvent(toolCallEndEvent.toolCallId(), toolCallEndEvent.timestamp(), rawEvent);
            case ToolCallChunkEvent toolCallChunkEvent ->
                new ToolCallChunkEvent(
                        toolCallChunkEvent.toolCallId(),
                        toolCallChunkEvent.toolCallName(),
                        toolCallChunkEvent.parentMessageId(),
                        toolCallChunkEvent.delta(),
                        toolCallChunkEvent.timestamp(),
                        rawEvent);
            case ToolCallResultEvent toolCallResultEvent ->
                new ToolCallResultEvent(
                        toolCallResultEvent.messageId(),
                        toolCallResultEvent.toolCallId(),
                        toolCallResultEvent.content(),
                        toolCallResultEvent.role(),
                        toolCallResultEvent.timestamp(),
                        rawEvent);
            case ReasoningStartEvent reasoningStartEvent ->
                new ReasoningStartEvent(reasoningStartEvent.messageId(), reasoningStartEvent.timestamp(), rawEvent);
            case ReasoningEndEvent reasoningEndEvent ->
                new ReasoningEndEvent(reasoningEndEvent.messageId(), reasoningEndEvent.timestamp(), rawEvent);
            case ReasoningMessageStartEvent reasoningMessageStartEvent ->
                new ReasoningMessageStartEvent(
                        reasoningMessageStartEvent.messageId(), reasoningMessageStartEvent.timestamp(), rawEvent);
            case ReasoningMessageContentEvent reasoningMessageContentEvent ->
                new ReasoningMessageContentEvent(
                        reasoningMessageContentEvent.messageId(),
                        reasoningMessageContentEvent.delta(),
                        reasoningMessageContentEvent.timestamp(),
                        rawEvent);
            case ReasoningMessageEndEvent reasoningMessageEndEvent ->
                new ReasoningMessageEndEvent(
                        reasoningMessageEndEvent.messageId(), reasoningMessageEndEvent.timestamp(), rawEvent);
            case ReasoningMessageChunkEvent reasoningMessageChunkEvent ->
                new ReasoningMessageChunkEvent(
                        reasoningMessageChunkEvent.messageId(),
                        reasoningMessageChunkEvent.delta(),
                        reasoningMessageChunkEvent.timestamp(),
                        rawEvent);
            case ReasoningEncryptedValueEvent reasoningEncryptedValueEvent ->
                new ReasoningEncryptedValueEvent(
                        reasoningEncryptedValueEvent.subtype(),
                        reasoningEncryptedValueEvent.entityId(),
                        reasoningEncryptedValueEvent.encryptedValue(),
                        reasoningEncryptedValueEvent.timestamp(),
                        rawEvent);
            case StateSnapshotEvent stateSnapshotEvent ->
                new StateSnapshotEvent(stateSnapshotEvent.snapshot(), stateSnapshotEvent.timestamp(), rawEvent);
            case StateDeltaEvent stateDeltaEvent ->
                new StateDeltaEvent(stateDeltaEvent.delta(), stateDeltaEvent.timestamp(), rawEvent);
            case MessagesSnapshotEvent messagesSnapshotEvent ->
                new MessagesSnapshotEvent(
                        messagesSnapshotEvent.messages(), messagesSnapshotEvent.timestamp(), rawEvent);
            case ActivitySnapshotEvent activitySnapshotEvent ->
                new ActivitySnapshotEvent(
                        activitySnapshotEvent.messageId(),
                        activitySnapshotEvent.activityType(),
                        activitySnapshotEvent.content(),
                        activitySnapshotEvent.replace(),
                        activitySnapshotEvent.timestamp(),
                        rawEvent);
            case ActivityDeltaEvent activityDeltaEvent ->
                new ActivityDeltaEvent(
                        activityDeltaEvent.messageId(),
                        activityDeltaEvent.activityType(),
                        activityDeltaEvent.patch(),
                        activityDeltaEvent.timestamp(),
                        rawEvent);
            case RawEvent rawSourceEvent ->
                new RawEvent(rawSourceEvent.event(), rawSourceEvent.source(), rawSourceEvent.timestamp(), rawEvent);
            case CustomEvent customEvent ->
                new CustomEvent(customEvent.name(), customEvent.value(), customEvent.timestamp(), rawEvent);
            case MetaEvent metaEvent ->
                new MetaEvent(metaEvent.metaType(), metaEvent.payload(), metaEvent.timestamp(), rawEvent);
        };
    }
}
