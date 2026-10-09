package com.agentengine.agent.infra.utils;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.util.agents.AgentFileDetails;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.common.utils.CollectionUtils;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.Collection;
import java.util.List;

/** Builds model content from what an API request carries: message parts and resume answers. */
public final class ContentUtils {

  private ContentUtils() {}

  /** Drops any non-text part (e.g. {@link MessagePart.BinaryPart}) rather than failing on it. */
  public static List<MessagePart.TextPart> textParts(final List<MessagePart> parts) {
    return CollectionUtils.nullSafeList(parts).stream()
        .filter(MessagePart.TextPart.class::isInstance)
        .map(MessagePart.TextPart.class::cast)
        .toList();
  }

  public static Content buildUserContent(
      final List<MessagePart.TextPart> textParts, final List<AgentFileDetails> knowledges) {
    final StringBuilder text = new StringBuilder();
    for (final MessagePart.TextPart textPart : textParts) {
      if (!text.isEmpty()) {
        text.append("\n");
      }
      text.append(textPart.text());
    }

    final Content content =
        Content.builder()
            .role(Constants.AUTHOR_USER)
            .parts(List.of(Part.fromText(text.toString())))
            .build();
    return com.agentengine.util.agents.ContentUtils.addAttachmentsToContent(content, knowledges);
  }

  public static Content buildResumeContent(final Collection<ResumeRequest> resumeRequests) {
    final List<Part> parts =
        CollectionUtils.nullSafeList(resumeRequests).stream()
            .map(
                resumeRequest ->
                    EventUtils.buildResumeAnswerEvent(
                        resumeRequest.getInterruptId(),
                        resumeRequest.getAccepted(),
                        resumeRequest.getAnswer()))
            .flatMap(event -> event.content().stream())
            .flatMap(content -> content.parts().stream())
            .flatMap(List::stream)
            .toList();
    return Content.builder().role(Constants.AUTHOR_USER).parts(parts).build();
  }
}
