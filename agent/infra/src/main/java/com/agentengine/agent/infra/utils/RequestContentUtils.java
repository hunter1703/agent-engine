package com.agentengine.agent.infra.utils;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.util.agents.AgentFileDetails;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.utils.CollectionUtils;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds model content from what an API request carries: message parts and resume answers. */
public final class RequestContentUtils {

  private static final Logger LOG = LoggerFactory.getLogger(RequestContentUtils.class);

  private RequestContentUtils() {}

  /** Drops any non-text part (e.g. {@link MessagePart.BinaryPart}) rather than failing on it. */
  public static List<MessagePart.TextPart> textParts(final List<MessagePart> parts) {
    return CollectionUtils.nullSafeList(parts).stream()
        .filter(MessagePart.TextPart.class::isInstance)
        .map(MessagePart.TextPart.class::cast)
        .toList();
  }

  public static Content buildUserContent(final List<MessagePart.TextPart> textParts) {
    final StringBuilder text = new StringBuilder();
    for (final MessagePart.TextPart textPart : textParts) {
      if (!text.isEmpty()) {
        text.append("\n");
      }
      text.append(textPart.text());
    }

    return Content.builder()
        .role(Constants.AUTHOR_USER)
        .parts(List.of(Part.fromText(text.toString())))
        .build();
  }

  /**
   * For one-shot temp runs without an indexing session: reads raw attachment bytes and appends them
   * as binary parts directly to the model content.
   */
  public static Content buildTempUserContent(
      final List<MessagePart.TextPart> textParts,
      final List<AgentFileDetails> attachments,
      final FileService fileService) {
    final Content content = buildUserContent(textParts);
    if (CollectionUtils.isEmpty(attachments) || fileService == null) {
      return content;
    }
    final List<Part> parts = new ArrayList<>(content.parts().orElse(List.of()));
    for (final AgentFileDetails attachment : attachments) {
      try (InputStream stream = fileService.getContent(attachment.toFileDetails())) {
        final byte[] bytes = stream.readAllBytes();
        if (bytes.length > 0) {
          parts.add(Part.fromBytes(bytes, attachment.mimeType()));
        }
      } catch (IOException ioException) {
        LOG.warn("Failed to read attachment file {}.", attachment.source(), ioException);
      }
    }
    return content.toBuilder().parts(parts).build();
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
