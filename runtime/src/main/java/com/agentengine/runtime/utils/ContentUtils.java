package com.agentengine.runtime.utils;

import com.agentengine.runtime.api.model.MessagePart;
import com.agentengine.runtime.api.model.UserMessage;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.FileDetails;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

import java.util.*;

/** Utilities for generic content/message extraction and indexing. */
public final class ContentUtils {

    private ContentUtils() {}

    public static String extractLatestUserText(final LlmRequest request) {
        if (request == null) {
            return "";
        }
        return extractLatestUserText(request.contents());
    }

    public static String extractLatestUserText(final List<Content> contents) {
        final List<Content> safeContents = CollectionUtils.nullSafeList(contents);
        for (int i = safeContents.size() - 1; i >= 0; i--) {
            final Content content = safeContents.get(i);
            if (content == null) {
                continue;
            }
            final Optional<String> role = content.role();
            if (role.isPresent() && !Constants.AUTHOR_USER.equalsIgnoreCase(role.get())) {
                continue;
            }
            final String text = content.text();
            if (StringUtils.isNotBlank(text)) {
                return text.trim();
            }
        }
        return "";
    }

    public static int findLatestUserContentIndex(final List<Content> contents) {
        final List<Content> safeContents = CollectionUtils.nullSafeList(contents);
        for (int i = safeContents.size() - 1; i >= 0; i--) {
            final Content content = safeContents.get(i);
            if (content == null) {
                continue;
            }
            final Optional<String> role = content.role();
            if (role.isEmpty() || Constants.AUTHOR_USER.equalsIgnoreCase(role.get())) {
                return i;
            }
        }
        return -1;
    }

    public static int estimateTokens(final Content content) {
        if (content == null) {
            return 0;
        }
        return StringUtils.estimateTextContent(content.text());
    }

    public static boolean isEmptyPart(final Content content) {
        if (content == null) {
            return true;
        }
        return content.parts().orElse(List.of()).stream().allMatch(ContentUtils::isEmptyPart);
    }

    public static boolean isEmptyPart(final Part part) {
        return part == null
                || (part.functionCall().isEmpty()
                        && part.functionResponse().isEmpty()
                        && part.codeExecutionResult().isEmpty()
                        && part.executableCode().isEmpty()
                        && part.fileData().isEmpty()
                        && part.inlineData().isEmpty()
                        && part.mediaResolution().isEmpty()
                        && part.videoMetadata().isEmpty()
                        && part.thoughtSignature().isEmpty()
                        && StringUtils.isBlank(part.text().orElse(null)));
    }

    public static List<Part> getToolCallParts(final Content content) {
        return content == null
                ? List.of()
                : content.parts().orElse(List.of()).stream()
                        .filter(part -> part.functionCall().isPresent())
                        .toList();
    }

    public static List<Part> getToolResponseParts(final Content content) {
        return content == null
                ? List.of()
                : content.parts().orElse(List.of()).stream()
                        .filter(part -> part.functionResponse().isPresent())
                        .toList();
    }

    public static boolean hasVisibleText(final Content content) {
        if (content == null) {
            return false;
        }
        return content.parts().orElse(List.of()).stream()
                .filter(part -> !part.thought().orElse(false))
                .map(Part::text)
                .flatMap(Optional::stream)
                .anyMatch(StringUtils::isNotBlank);
    }

    public static List<Content> stripThoughtParts(final List<Content> contents) {
        return CollectionUtils.nullSafeList(contents).stream()
                .map(content -> content.toBuilder()
                        .parts(content.parts().orElse(List.of()).stream()
                                .filter(part -> !part.thought().orElse(false))
                                .toList())
                        .build())
                .filter(content -> !content.parts().orElse(List.of()).isEmpty())
                .toList();
    }

    public static Content stripNonToolParts(final Content content) {
        return content.toBuilder()
                .parts(content.parts().orElse(List.of()).stream()
                        .filter(part -> part.functionCall().isEmpty()
                                && part.functionResponse().isEmpty())
                        .toList())
                .build();
    }

    public static Content buildUserContent(final UserMessage userMessage) {
        final StringBuilder text = new StringBuilder();
        final List<String> artifactNames = new ArrayList<>();
        for (final MessagePart part : userMessage.parts()) {
            switch (part) {
                case MessagePart.TextPart textPart -> {
                    if (!text.isEmpty()) {
                        text.append("\n");
                    }
                    text.append(textPart.text());
                }
                case MessagePart.FilePart filePart -> artifactNames.add(filePart.fileDetails().name());
                default -> throw new IllegalStateException("Unexpected value: " + part);
            }
        }

        if (CollectionUtils.isNotEmpty(artifactNames)) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append("Here are the artifact names: ").append(JsonUtils.toJson(artifactNames));
        }

        return Content.builder()
                .role(Constants.AUTHOR_USER)
                .parts(List.of(Part.fromText(text.toString())))
                .build();
    }

    public static Content buildConfirmationsContent(final Collection<Confirmation> confirmations) {
        final List<Part> parts = CollectionUtils.nullSafeList(confirmations).stream()
                .map(confirmation -> EventUtils.buildConfirmationEvent(
                        confirmation.getConfirmationId(), confirmation.getConfirmed(), confirmation.getAnswer()))
                .flatMap(event -> event.content().stream())
                .flatMap(content -> content.parts().stream())
                .flatMap(List::stream)
                .toList();
        return Content.builder().role(Constants.AUTHOR_USER).parts(parts).build();
    }
}
