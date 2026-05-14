package com.agentengine.util.agents.agui;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.FileDetails;
import com.agui.core.types.BaseEvent;
import com.agui.core.types.Role;
import com.agui.core.types.TextMessageChunkEvent;
import com.agui.core.types.TextMessageEndEvent;
import com.agui.core.types.TextMessageStartEvent;
import com.agui.core.types.ThinkingEndEvent;
import com.agui.core.types.ThinkingStartEvent;
import com.agui.core.types.ThinkingTextMessageContentEvent;
import com.agui.core.types.ThinkingTextMessageEndEvent;
import com.agui.core.types.ThinkingTextMessageStartEvent;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AGUITextMapper {

    private static final Logger LOG = LoggerFactory.getLogger(AGUITextMapper.class);

    private final AGUIMapperState state;
    private AGUIEventMapper.Mode mode;

    public AGUITextMapper(final AGUIMapperState state, final AGUIEventMapper.Mode mode) {
        this.state = state;
        this.mode = mode;
    }

    public void switchToLiveMode() {
        mode = AGUIEventMapper.Mode.LIVE;
    }

    public Flowable<BaseEvent> mapThought(final String thoughtText, final boolean partial) {
        if (StringUtils.isEmpty(thoughtText)) {
            return Flowable.empty();
        }

        Flowable<BaseEvent> flowable = Flowable.empty();
        if (state.hasOpenTextMessage()) {
            flowable = flowable.concatWith(finalizeTextMessageIfNeeded());
        }
        flowable = flowable.concatWith(startReasoningIfNeeded())
                .concatWith(startReasoningMessageIfNeeded())
                .concatWith(mapReasoningContent(thoughtText));
        if (!partial) {
            flowable = flowable.concatWith(endReasoningMessageIfNeeded()).concatWith(endReasoningIfNeeded());
        }
        return flowable;
    }

    public Flowable<BaseEvent> mapText(final String text, final boolean partial) {
        if (StringUtils.isEmpty(text)) {
            return Flowable.empty();
        }
        return closeReasoningIfNeeded()
                .concatWith(startTextMessageIfNeeded())
                .concatWith(mapTextMessageContent(text, partial))
                .concatWith(endTextMessageIfNeeded(partial));
    }

    public Flowable<BaseEvent> finalizeOpenContent() {
        return finalizeTextMessageIfNeeded().concatWith(closeReasoningIfNeeded());
    }

    public Flowable<BaseEvent> mapAttachment(final FileDetails fileDetails) {
        final Flowable<BaseEvent> flowable = startTextMessageIfNeeded();
        final String parentMessageId = state.currentTextMessageId();
        final long timestamp = state.timestamp();
        LOG.debug(
                "Generated output event - eventType=AttachmentEvent, parentMessageId={}, fileDetails={}",
                parentMessageId,
                JsonUtils.toJson(fileDetails));
        return flowable.concatWith(
                Flowable.just(AGUIUtils.buildAttachmentEvent(parentMessageId, fileDetails, timestamp)));
    }

    public Flowable<BaseEvent> closeReasoningIfNeeded() {
        if (!state.hasOpenReasoning()) {
            return Flowable.empty();
        }
        return endReasoningMessageIfNeeded().concatWith(endReasoningIfNeeded());
    }

    public Flowable<BaseEvent> mapUserMessage(final SessionEvent event) {
        // Record the source event so the generated message ID is based on this event's ID,
        // not the stale ID from the previous (assistant) event — which would cause collisions.
        state.recordSourceEvent(event);
        final String text = event.getContent() != null
                ? event.getContent()
                        .parts()
                        .flatMap(parts -> {
                            final String textContent = parts.stream()
                                    .map(part -> part.text().orElse(""))
                                    .filter(StringUtils::isNotBlank)
                                    .findFirst()
                                    .orElse(null);
                            if (textContent != null) {
                                return Optional.of(textContent);
                            }
                            // Image-only message: emit a placeholder so the replay shows
                            // something meaningful instead of a null delta.
                            final boolean hasImages = parts.stream()
                                    .anyMatch(part -> part.inlineData().isPresent());
                            return hasImages ? java.util.Optional.of("[Image]") : Optional.empty();
                        })
                        .orElse(null)
                : null;
        final String messageId = state.nextReplayTextMessageId(event.getId());

        final TextMessageStartEvent start = new TextMessageStartEvent(messageId, Role.USER, state.timestamp(), null);
        final TextMessageChunkEvent content =
                new TextMessageChunkEvent(messageId, Role.USER, text, state.timestamp(), null);
        final TextMessageEndEvent end = new TextMessageEndEvent(messageId, state.timestamp(), null);

        LOG.debug("Generated user message events for replay - msgId={}", messageId);
        return Flowable.just(start, content, end);
    }

    private Flowable<BaseEvent> startReasoningIfNeeded() {
        if (state.hasOpenReasoning()) {
            LOG.debug("Already in reasoning state, skipping ThinkingStartEvent generation");
            return Flowable.empty();
        }
        state.startReasoning();
        final ThinkingStartEvent event = new ThinkingStartEvent(null, state.timestamp(), null);
        LOG.debug("Generated output event - eventType=ThinkingStartEvent");
        return Flowable.just(event);
    }

    private Flowable<BaseEvent> startReasoningMessageIfNeeded() {
        if (state.hasOpenReasoningMessage()) {
            LOG.debug("Reasoning message already open, skipping ThinkingTextMessageStartEvent generation");
            return Flowable.empty();
        }
        state.startReasoningMessage();
        final ThinkingTextMessageStartEvent event = new ThinkingTextMessageStartEvent(state.timestamp(), null);
        LOG.debug("Generated output event - eventType=ThinkingTextMessageStartEvent");
        return Flowable.just(event);
    }

    private Flowable<BaseEvent> mapReasoningContent(final String text) {
        if (StringUtils.isEmpty(text) || !state.hasOpenReasoningMessage()) {
            return Flowable.empty();
        }
        final ThinkingTextMessageContentEvent event =
                new ThinkingTextMessageContentEvent(text, state.timestamp(), null);
        LOG.debug("Generated output event - eventType=ThinkingTextMessageContentEvent");
        return Flowable.just(event);
    }

    private Flowable<BaseEvent> endReasoningMessageIfNeeded() {
        if (!state.hasOpenReasoningMessage()) {
            return Flowable.empty();
        }
        state.closeReasoningMessage();
        final ThinkingTextMessageEndEvent event = new ThinkingTextMessageEndEvent(state.timestamp(), null);
        LOG.debug("Generated output event - eventType=ThinkingTextMessageEndEvent");
        return Flowable.just(event);
    }

    private Flowable<BaseEvent> endReasoningIfNeeded() {
        if (!state.hasOpenReasoning()) {
            return Flowable.empty();
        }
        state.closeReasoning();
        final ThinkingEndEvent event = new ThinkingEndEvent(state.timestamp(), null);
        LOG.debug("Generated output event - eventType=ThinkingEndEvent");
        return Flowable.just(event);
    }

    private Flowable<BaseEvent> startTextMessageIfNeeded() {
        if (state.hasOpenTextMessage()) {
            LOG.debug("Text message already in progress, skipping TextMessageStartEvent generation");
            return Flowable.empty();
        }
        final TextMessageStartEvent start =
                new TextMessageStartEvent(state.startNextTextMessage(), Role.ASSISTANT, state.timestamp(), null);
        LOG.debug("Generated output event - eventType=TextMessageStartEvent, msgId={}", start.getMessageId());
        return Flowable.just(start);
    }

    private Flowable<BaseEvent> mapTextMessageContent(final String text, final boolean partial) {
        if (StringUtils.isEmpty(text)) {
            return Flowable.empty();
        }
        LOG.debug(
                "Processing message mapping - msgId={}, partial={}, isNewText={}",
                state.currentTextMessageId(),
                partial,
                state.isTextBufferEmpty());
        if (!partial) {
            if (state.isTextBufferEmpty()) {
                state.appendText(text);
            }
            return Flowable.empty();
        }

        state.appendText(text);
        // In REPLAY mode buffer all chunks; the full accumulated text is emitted as a single
        // chunk when the message ends, so the client renders history instantly rather than
        // re-animating every original token.
        if (mode == AGUIEventMapper.Mode.REPLAY) {
            return Flowable.empty();
        }
        final TextMessageChunkEvent chunk =
                new TextMessageChunkEvent(state.currentTextMessageId(), Role.ASSISTANT, text, state.timestamp(), null);
        LOG.debug("Generated output event - eventType=TextMessageChunkEvent, msgId={}", chunk.getMessageId());
        return Flowable.just(chunk);
    }

    private Flowable<BaseEvent> endTextMessageIfNeeded(final boolean partial) {
        if (partial) {
            LOG.debug("Partial message, skipping TextMessageEndEvent generation");
            return Flowable.empty();
        }
        return emitTextMessageEnd();
    }

    private Flowable<BaseEvent> finalizeTextMessageIfNeeded() {
        if (!state.hasOpenTextMessage()) {
            return Flowable.empty();
        }
        return emitTextMessageEnd();
    }

    private Flowable<BaseEvent> emitTextMessageEnd() {
        if (!state.hasOpenTextMessage()) {
            return Flowable.empty();
        }

        final String messageId = state.currentTextMessageId();
        final String finalAnswer = state.completeTextMessage();
        state.resetTextMessage();

        Flowable<BaseEvent> flowable = Flowable.empty();
        if (mode == AGUIEventMapper.Mode.REPLAY && StringUtils.isNotBlank(finalAnswer)) {
            final TextMessageChunkEvent content =
                    new TextMessageChunkEvent(messageId, Role.ASSISTANT, finalAnswer, state.timestamp(), null);
            LOG.debug("Generated output event - eventType=TextMessageChunkEvent (replay), msgId={}", messageId);
            flowable = flowable.concatWith(Flowable.just(content));
        }

        final TextMessageEndEvent end = new TextMessageEndEvent(messageId, state.timestamp(), null);
        LOG.debug("Generated output event - eventType=TextMessageEndEvent, msgId={}", end.getMessageId());
        return flowable.concatWith(Flowable.just(end));
    }
}
