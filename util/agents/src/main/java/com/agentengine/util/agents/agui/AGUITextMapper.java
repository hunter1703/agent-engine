package com.agentengine.util.agents.agui;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.StringUtils;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.TextMessageChunkEvent;
import com.agui.core.event.TextMessageContentEvent;
import com.agui.core.event.TextMessageEndEvent;
import com.agui.core.event.TextMessageStartEvent;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AGUITextMapper {

    private static final Logger LOG = LoggerFactory.getLogger(AGUITextMapper.class);

    private final AGUIMapperState state;
    private final AGUIEventDecorator decorator;

    public AGUITextMapper(final AGUIMapperState state, final AGUIEventDecorator decorator) {
        this.state = state;
        this.decorator = decorator;
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

    public Flowable<BaseEvent> closeReasoningIfNeeded() {
        if (!state.hasOpenReasoning()) {
            return Flowable.empty();
        }
        return endReasoningMessageIfNeeded().concatWith(endReasoningIfNeeded());
    }

    public Flowable<BaseEvent> mapUserMessage(final SessionEvent event) {
        final String text = event.getContent() != null
                ? event.getContent()
                        .parts()
                        .flatMap(parts -> parts.stream()
                                .map(part -> part.text().orElse(""))
                                .filter(StringUtils::isNotBlank)
                                .findFirst())
                        .orElse(null)
                : null;
        final String messageId = state.nextReplayTextMessageId(event.getId());

        final TextMessageStartEvent start = new TextMessageStartEvent();
        start.setMessageId(messageId);
        start.setRole("user");
        decorator.decorate(start);

        final TextMessageContentEvent content = new TextMessageContentEvent();
        content.setMessageId(messageId);
        content.setDelta(text);
        decorator.decorate(content);

        final TextMessageEndEvent end = new TextMessageEndEvent();
        end.setMessageId(messageId);
        decorator.decorate(end);

        LOG.debug("Generated user message events for replay - msgId={}", messageId);
        return Flowable.just(start, content, end);
    }

    private Flowable<BaseEvent> startReasoningIfNeeded() {
        if (state.hasOpenReasoning()) {
            LOG.debug("Already in reasoning state, skipping ReasoningStartEvent generation");
            return Flowable.empty();
        }
        final ReasoningStartEvent event = new ReasoningStartEvent();
        event.setMessageId(state.startNextReasoning());
        decorator.decorate(event);
        LOG.debug("Generated output event - eventType=ReasoningStartEvent, messageId={}", event.getMessageId());
        return Flowable.just(event);
    }

    private Flowable<BaseEvent> startReasoningMessageIfNeeded() {
        if (state.hasOpenReasoningMessage()) {
            LOG.debug("Reasoning message already open, skipping ReasoningMessageStartEvent generation");
            return Flowable.empty();
        }
        final ReasoningMessageStartEvent event = new ReasoningMessageStartEvent();
        event.setMessageId(state.startNextReasoningMessage());
        event.setRole("assistant");
        decorator.decorate(event);
        LOG.debug("Generated output event - eventType=ReasoningMessageStartEvent, messageId={}", event.getMessageId());
        return Flowable.just(event);
    }

    private Flowable<BaseEvent> mapReasoningContent(final String text) {
        if (StringUtils.isEmpty(text) || !state.hasOpenReasoningMessage()) {
            return Flowable.empty();
        }
        final ReasoningMessageContentEvent event = new ReasoningMessageContentEvent();
        event.setMessageId(state.currentReasoningMessageId());
        event.setDelta(text);
        decorator.decorate(event);
        LOG.debug("Generated output event - eventType=ReasoningMessageContentEvent");
        return Flowable.just(event);
    }

    private Flowable<BaseEvent> endReasoningMessageIfNeeded() {
        if (!state.hasOpenReasoningMessage()) {
            return Flowable.empty();
        }
        final ReasoningMessageEndEvent event = new ReasoningMessageEndEvent();
        event.setMessageId(state.currentReasoningMessageId());
        decorator.decorate(event);
        state.closeReasoningMessage();
        LOG.debug("Generated output event - eventType=ReasoningMessageEndEvent");
        return Flowable.just(event);
    }

    private Flowable<BaseEvent> endReasoningIfNeeded() {
        if (!state.hasOpenReasoning()) {
            return Flowable.empty();
        }
        final ReasoningEndEvent event = new ReasoningEndEvent();
        event.setMessageId(state.currentReasoningId());
        decorator.decorate(event);
        state.closeReasoning();
        LOG.debug("Generated output event - eventType=ReasoningEndEvent");
        return Flowable.just(event);
    }

    private Flowable<TextMessageStartEvent> startTextMessageIfNeeded() {
        if (state.hasOpenTextMessage()) {
            LOG.debug("Text message already in progress, skipping TextMessageStartEvent generation");
            return Flowable.empty();
        }
        final TextMessageStartEvent start = new TextMessageStartEvent();
        start.setMessageId(state.startNextTextMessage());
        start.setRole("assistant");
        decorator.decorate(start);
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
        final TextMessageChunkEvent chunk = new TextMessageChunkEvent();
        chunk.setMessageId(state.currentTextMessageId());
        chunk.setDelta(text);
        decorator.decorate(chunk);
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
        Flowable<BaseEvent> flowable = Flowable.empty();
        if (StringUtils.isNotBlank(finalAnswer)) {
            final TextMessageContentEvent content = new TextMessageContentEvent();
            content.setMessageId(messageId);
            content.setDelta(finalAnswer);
            decorator.decorate(content);
            LOG.debug("Generated output event - eventType=TextMessageContentEvent, msgId={}", content.getMessageId());
            flowable = flowable.concatWith(Flowable.just(content));
        }

        final TextMessageEndEvent end = new TextMessageEndEvent();
        end.setMessageId(messageId);
        decorator.decorate(end);
        LOG.debug("Generated output event - eventType=TextMessageEndEvent, msgId={}", end.getMessageId());
        state.resetTextMessage();
        return flowable.concatWith(Flowable.just(end));
    }
}
