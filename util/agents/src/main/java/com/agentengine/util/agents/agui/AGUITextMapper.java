package com.agentengine.util.agents.agui;

import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.FileDetails;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.TextMessageChunkEvent;
import com.agui.core.event.TextMessageEndEvent;
import com.agui.core.event.TextMessageStartEvent;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AGUITextMapper {

    private static final Logger LOG = LoggerFactory.getLogger(AGUITextMapper.class);

    private final AGUIMapperState state;
    private final AGUIEventDecorator decorator;
    private AGUIEventMapper.Mode mode;

    public AGUITextMapper(
            final AGUIMapperState state, final AGUIEventDecorator decorator, final AGUIEventMapper.Mode mode) {
        this.state = state;
        this.decorator = decorator;
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
        final AttachmentEvent event = new AttachmentEvent(parentMessageId, fileDetails);
        decorator.decorate(event);
        LOG.debug(
                "Generated output event - eventType=AttachmentEvent, parentMessageId={}, fileDetails={}",
                parentMessageId,
                JsonUtils.toJson(fileDetails));
        return flowable.concatWith(Flowable.just(event));
    }

    private static String generateFileName(final String mimeType) {
        if (mimeType == null) {
            return "attachment";
        }
        final String ext =
                switch (mimeType) {
                    case "image/png" -> "png";
                    case "image/jpeg" -> "jpg";
                    case "image/gif" -> "gif";
                    case "image/webp" -> "webp";
                    case "image/svg+xml" -> "svg";
                    case "application/pdf" -> "pdf";
                    default -> mimeType.contains("/") ? mimeType.substring(mimeType.indexOf('/') + 1) : "bin";
                };
        return "attachment." + ext;
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

        final TextMessageStartEvent start = new TextMessageStartEvent();
        start.setMessageId(messageId);
        start.setRole(Constants.AUTHOR_USER);
        decorator.decorate(start);

        final TextMessageChunkEvent content = new TextMessageChunkEvent();
        content.setMessageId(messageId);
        content.setDelta(text);
        content.setRole(Constants.AUTHOR_USER);
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

    private Flowable<BaseEvent> startTextMessageIfNeeded() {
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
        // In REPLAY mode buffer all chunks; the full accumulated text is emitted as a single
        // chunk when the message ends, so the client renders history instantly rather than
        // re-animating every original token.
        if (mode == AGUIEventMapper.Mode.REPLAY) {
            return Flowable.empty();
        }
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
        state.resetTextMessage();

        Flowable<BaseEvent> flowable = Flowable.empty();
        if (mode == AGUIEventMapper.Mode.REPLAY && StringUtils.isNotBlank(finalAnswer)) {
            final TextMessageChunkEvent content = new TextMessageChunkEvent();
            content.setMessageId(messageId);
            content.setDelta(finalAnswer);
            content.setRole("assistant");
            decorator.decorate(content);
            LOG.debug("Generated output event - eventType=TextMessageChunkEvent (replay), msgId={}", messageId);
            flowable = flowable.concatWith(Flowable.just(content));
        }

        final TextMessageEndEvent end = new TextMessageEndEvent();
        end.setMessageId(messageId);
        decorator.decorate(end);
        LOG.debug("Generated output event - eventType=TextMessageEndEvent, msgId={}", end.getMessageId());
        return flowable.concatWith(Flowable.just(end));
    }
}
