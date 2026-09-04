package com.agentengine.util.agents.agui;

import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.FileDetails;
import com.agui.community.core.event.*;
import com.agui.community.core.message.Role;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AGUITextMapper {

  private static final Logger LOG = LoggerFactory.getLogger(AGUITextMapper.class);

  private final AGUIMapperState state;

  public AGUITextMapper(final AGUIMapperState state) {
    this.state = state;
  }

  public Flowable<Event> mapThought(final String thoughtText, final boolean partial) {
    if (StringUtils.isEmpty(thoughtText)) {
      return Flowable.empty();
    }

    Flowable<Event> flowable = Flowable.empty();
    if (state.hasOpenTextMessage()) {
      flowable = flowable.concatWith(finalizeTextMessageIfNeeded());
    }
    flowable =
        flowable
            .concatWith(startReasoningIfNeeded())
            .concatWith(startReasoningMessageIfNeeded())
            .concatWith(mapReasoningContent(thoughtText));
    if (!partial) {
      flowable =
          flowable.concatWith(endReasoningMessageIfNeeded()).concatWith(endReasoningIfNeeded());
    }
    return flowable;
  }

  public Flowable<Event> mapText(final String text, final boolean partial) {
    if (StringUtils.isEmpty(text)) {
      return Flowable.empty();
    }
    return closeReasoningIfNeeded()
        .concatWith(startTextMessageIfNeeded())
        .concatWith(mapTextMessageContent(text))
        .concatWith(endTextMessageIfNeeded(partial));
  }

  public Flowable<Event> finalizeOpenContent() {
    return finalizeTextMessageIfNeeded().concatWith(closeReasoningIfNeeded());
  }

  public Flowable<Event> mapAttachment(final FileDetails fileDetails) {
    final Flowable<Event> flowable = startTextMessageIfNeeded();
    final String parentMessageId = state.currentTextMessageId();
    final long timestamp = state.timestamp();
    LOG.debug(
        "Generated output event - eventType=AttachmentEvent, parentMessageId={}, fileDetails={}",
        parentMessageId,
        JsonUtils.toJson(fileDetails));
    return flowable.concatWith(
        Flowable.just(AGUIUtils.buildAttachmentEvent(parentMessageId, fileDetails, timestamp)));
  }

  public Flowable<Event> closeReasoningIfNeeded() {
    if (!state.hasOpenReasoning()) {
      return Flowable.empty();
    }
    return endReasoningMessageIfNeeded().concatWith(endReasoningIfNeeded());
  }

  // The current event's own content role, not who sent it — content one agent addresses to
  // another (e.g. a spawn/send_message turn) carries content role "user" while its author is the
  // sending agent's id, not the literal string "user".
  private Role resolveRole() {
    return Role.USER.value().equals(state.currentRole()) ? Role.USER : Role.ASSISTANT;
  }

  private Flowable<Event> startReasoningIfNeeded() {
    if (state.hasOpenReasoning()) {
      LOG.debug("Already in reasoning state, skipping ThinkingStartEvent generation");
      return Flowable.empty();
    }
    final ReasoningStartEvent event =
        new ReasoningStartEvent(state.startReasoning(), state.timestamp(), null);
    LOG.debug("Generated output event - eventType=ThinkingStartEvent");
    return Flowable.just(event);
  }

  private Flowable<Event> startReasoningMessageIfNeeded() {
    if (state.hasOpenReasoningMessage()) {
      LOG.debug(
          "Reasoning message already open, skipping ThinkingTextMessageStartEvent generation");
      return Flowable.empty();
    }
    final String reasoningMessageId = state.startReasoningMessage();
    final ReasoningMessageStartEvent event =
        new ReasoningMessageStartEvent(reasoningMessageId, state.timestamp(), null);
    LOG.debug("Generated output event - eventType=ThinkingTextMessageStartEvent");
    return Flowable.just(event);
  }

  private Flowable<Event> mapReasoningContent(final String text) {
    if (StringUtils.isEmpty(text) || !state.hasOpenReasoningMessage()) {
      return Flowable.empty();
    }
    final ReasoningMessageContentEvent event =
        new ReasoningMessageContentEvent(
            state.currentReasoningMessageId(), text, state.timestamp(), null);
    LOG.debug("Generated output event - eventType=ReasoningMessageContentEvent");
    return Flowable.just(event);
  }

  private Flowable<Event> endReasoningMessageIfNeeded() {
    if (!state.hasOpenReasoningMessage()) {
      return Flowable.empty();
    }
    final String reasoningMessageId = state.currentReasoningMessageId();
    state.closeReasoningMessage();
    final ReasoningMessageEndEvent event =
        new ReasoningMessageEndEvent(reasoningMessageId, state.timestamp(), null);
    LOG.debug("Generated output event - eventType=ThinkingTextMessageEndEvent");
    return Flowable.just(event);
  }

  private Flowable<Event> endReasoningIfNeeded() {
    if (!state.hasOpenReasoning()) {
      return Flowable.empty();
    }
    // Capture the id before closing — closeReasoning() clears it from state.
    final String reasoningId = state.currentReasoningId();
    state.closeReasoning();
    final ReasoningEndEvent event = new ReasoningEndEvent(reasoningId, state.timestamp(), null);
    LOG.debug("Generated output event - eventType=ThinkingEndEvent");
    return Flowable.just(event);
  }

  private Flowable<Event> startTextMessageIfNeeded() {
    if (state.hasOpenTextMessage()) {
      LOG.debug("Text message already in progress, skipping TextMessageStartEvent generation");
      return Flowable.empty();
    }
    final TextMessageStartEvent start =
        new TextMessageStartEvent(
            state.startNextTextMessage(), resolveRole(), state.timestamp(), null);
    LOG.debug(
        "Generated output event - eventType=TextMessageStartEvent, msgId={}", start.messageId());
    return Flowable.just(start);
  }

  private Flowable<Event> mapTextMessageContent(final String text) {
    if (StringUtils.isEmpty(text)) {
      return Flowable.empty();
    }
    final TextMessageChunkEvent chunk =
        new TextMessageChunkEvent(
            state.currentTextMessageId(), resolveRole(), text, state.timestamp(), null);
    LOG.debug(
        "Generated output event - eventType=TextMessageChunkEvent, msgId={}", chunk.messageId());
    return Flowable.just(chunk);
  }

  private Flowable<Event> endTextMessageIfNeeded(final boolean partial) {
    if (partial) {
      LOG.debug("Partial message, skipping TextMessageEndEvent generation");
      return Flowable.empty();
    }
    return emitTextMessageEnd();
  }

  private Flowable<Event> finalizeTextMessageIfNeeded() {
    if (!state.hasOpenTextMessage()) {
      return Flowable.empty();
    }
    return emitTextMessageEnd();
  }

  private Flowable<Event> emitTextMessageEnd() {
    if (!state.hasOpenTextMessage()) {
      return Flowable.empty();
    }
    final String messageId = state.currentTextMessageId();
    state.resetTextMessage();
    final TextMessageEndEvent end = new TextMessageEndEvent(messageId, state.timestamp(), null);
    LOG.debug("Generated output event - eventType=TextMessageEndEvent, msgId={}", end.messageId());
    return Flowable.just(end);
  }
}
