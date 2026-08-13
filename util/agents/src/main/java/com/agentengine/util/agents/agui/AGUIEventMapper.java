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

    @Override
    public Flowable<Event> onComplete() {
        return Flowable.empty();
    }

    @Override
    public Flowable<Event> onError(final Throwable throwable) {
        LOG.debug("Processing error mapping - throwable={}", ExceptionUtils.getErrorMessage(throwable));
        final RunErrorEvent errorEvent =
                new RunErrorEvent(ExceptionUtils.getErrorMessage(throwable), null, state.timestamp(), null);
        LOG.debug("Generated output event in onError - eventType=RunErrorEvent");
        return Flowable.just(errorEvent);
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
}
