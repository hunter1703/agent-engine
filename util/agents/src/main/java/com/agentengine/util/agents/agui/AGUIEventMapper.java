package com.agentengine.util.agents.agui;

import static com.google.adk.flows.llmflows.Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME;

import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.CorrectionMetadata;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.EventMapper;
import com.agentengine.util.common.ExceptionUtils;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.RunErrorEvent;
import com.agui.core.event.RunFinishedEvent;
import com.agui.core.event.RunStartedEvent;
import com.agui.core.event.StepFinishedEvent;
import com.agui.core.event.StepStartedEvent;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps runtime SessionEvent to AGUI events */
public final class AGUIEventMapper implements EventMapper<SessionEvent, BaseEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(AGUIEventMapper.class);

    public enum Mode {
        LIVE,
        REPLAY
    }

    private final Mode mode;
    private final AGUIMapperState state;
    private final AGUIEventDecorator decorator;
    private final AGUITextMapper textMapper;
    private final AGUIToolCallMapper toolCallMapper;

    public AGUIEventMapper(final String sessionId, final String agentId) {
        this(sessionId, agentId, Mode.LIVE);
    }

    public AGUIEventMapper(final String sessionId, final String agentId, final Mode mode) {
        this.mode = mode;
        this.state = new AGUIMapperState(sessionId, agentId);
        this.decorator = new AGUIEventDecorator(state);
        this.textMapper = new AGUITextMapper(state, decorator);
        this.toolCallMapper = new AGUIToolCallMapper(state, decorator);
    }

    @Override
    public Flowable<BaseEvent> map(final SessionEvent event) {
        LOG.debug("Input event received for mapping - eventId={}, author={}", event.getId(), event.getAuthor());
        if (SessionEvent.AUTHOR_USER.equalsIgnoreCase(event.getAuthor())) {
            return mode == Mode.REPLAY ? textMapper.mapUserMessage(event) : Flowable.empty();
        }

        state.recordSourceEvent(event);
        Flowable<BaseEvent> eventFlow = Flowable.empty();
        if (state.hasNewRun(event.getRunId())) {
            eventFlow = eventFlow.concatWith(startRun(event.getRunId()));
        }

        return eventFlow.concatWith(mapEventInternal(event)).concatWith(finishRunIfNeeded(event));
    }

    @Override
    public Flowable<BaseEvent> onComplete() {
        return Flowable.empty();
    }

    @Override
    public Flowable<BaseEvent> onError(final Throwable throwable) {
        LOG.debug("Processing error mapping - throwable={}", ExceptionUtils.getErrorMessage(throwable));
        final RunErrorEvent errorEvent = new RunErrorEvent();
        errorEvent.setError(ExceptionUtils.getErrorMessage(throwable));
        errorEvent.setRawEvent(Map.of("exception", ExceptionUtils.getStackstrace(throwable)));
        LOG.debug("Generated output event in onError - eventType=RunErrorEvent");
        return Flowable.just(decorator.decorate(errorEvent));
    }

    private Flowable<BaseEvent> mapEventInternal(final SessionEvent event) {
        Flowable<BaseEvent> flowable = startStepIfNeeded();
        if (SessionEventUtils.isCorrectionEvent(event)) {
            return flowable.concatWith(mapCorrectionEvent(event)).concatWith(finishStepIfNeeded(event));
        }

        final Optional<Content> content = Optional.ofNullable(event.getContent());
        if (content.isEmpty()) {
            return flowable.concatWith(finishStepIfNeeded(event));
        }

        final boolean partial = Boolean.TRUE.equals(event.isPartial());
        final boolean internal = SessionEventUtils.isInternal(event);
        for (final Part part : content.get().parts().orElse(List.of())) {
            flowable = flowable.concatWith(mapPart(part, partial, internal));
        }
        return flowable.concatWith(finishStepIfNeeded(event));
    }

    private Flowable<BaseEvent> mapPart(final Part part, final boolean partial, final boolean internal) {
        if (part.thought().orElse(false) || internal) {
            return textMapper.mapThought(part.text().orElse(""), partial);
        }

        Flowable<BaseEvent> flowable = Flowable.empty();
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

    private Flowable<BaseEvent> startRun(final String runId) {
        state.startRun(runId);
        final RunStartedEvent event = new RunStartedEvent();
        event.setRunId(state.currentRunId());
        event.setThreadId(state.sessionId());
        decorator.decorate(event);
        LOG.debug("Generated output event - eventType=RunStartedEvent, runId={}", event.getRunId());
        return Flowable.just(event);
    }

    private Flowable<BaseEvent> finishRunIfNeeded(final SessionEvent event) {
        if (event.getFinishReason() == null) {
            return Flowable.empty();
        }

        final RunFinishedEvent finishedEvent = new RunFinishedEvent();
        finishedEvent.setThreadId(state.sessionId());
        finishedEvent.setRunId(state.finishRun());
        if (state.finalAnswer() != null) {
            finishedEvent.setResult(state.finalAnswer());
        }
        decorator.decorate(finishedEvent);
        LOG.debug("Generated output event - eventType=RunFinishedEvent, runId={}", finishedEvent.getRunId());
        return Flowable.just(finishedEvent);
    }

    private Flowable<BaseEvent> startStepIfNeeded() {
        if (state.hasStartedStep()) {
            LOG.debug("Step already started, skipping StepStartedEvent generation");
            return Flowable.empty();
        }

        final StepStartedEvent stepEvent = new StepStartedEvent();
        stepEvent.setStepName(state.startNextStep());
        decorator.decorate(stepEvent);
        LOG.debug("Generated output event - eventType=StepStartedEvent, stepName={}", stepEvent.getStepName());
        return Flowable.just(stepEvent);
    }

    private Flowable<BaseEvent> finishStepIfNeeded(final SessionEvent event) {
        if (!Boolean.TRUE.equals(event.isTurnComplete()) || !state.hasStartedStep()) {
            return Flowable.empty();
        }
        return finishStep();
    }

    private Flowable<BaseEvent> finishStep() {
        final StepFinishedEvent event = new StepFinishedEvent();
        event.setStepName(state.finishStep());
        decorator.decorate(event);
        LOG.debug("Generated output event - eventType=StepFinishedEvent, stepName={}", event.getStepName());
        return textMapper.finalizeOpenContent().concatWith(Flowable.just(event));
    }

    private Flowable<BaseEvent> mapCorrectionEvent(final SessionEvent event) {
        final CorrectionMetadata correctionMetadata = SessionEventUtils.extractCorrectionMetadata(event);
        if (correctionMetadata == null) {
            return Flowable.empty();
        }

        final CorrectionEvent correctionEvent = new CorrectionEvent(correctionMetadata);
        decorator.decorate(correctionEvent);
        LOG.debug("Generated correction event - correctionMetadataPresent=true");
        return Flowable.just(correctionEvent);
    }
}
