package com.agentengine.runtime.utils;

import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.events.Event;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.flows.llmflows.Functions;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
            if (role.isPresent() && !"user".equalsIgnoreCase(role.get())) {
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
            if (role.isEmpty() || "user".equalsIgnoreCase(role.get())) {
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
        return content.parts().orElse(List.of()).stream()
                .filter(part -> part.functionCall().isPresent())
                .toList();
    }

    public static List<Part> getToolResponseParts(final Content content) {
        return content.parts().orElse(List.of()).stream()
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

    public static Content stripToolParts(final Content content) {
        return content.toBuilder()
                .parts(content.parts().orElse(List.of()).stream()
                        .filter(part -> part.functionCall().isEmpty()
                                && part.functionResponse().isEmpty())
                        .toList())
                .build();
    }

    public static String recentUser(final List<Event> events, final int max) {
        if (events == null || events.isEmpty()) {
            return "";
        }
        final List<String> intents = new ArrayList<>();
        for (int i = events.size() - 1; i >= 0 && intents.size() < max; i--) {
            final Content content = events.get(i).content().orElse(null);
            if (content == null || !"user".equals(content.role().orElse(""))) {
                continue;
            }
            final String text = content.text();
            if (StringUtils.isNotBlank(text)) {
                intents.add(text);
            }
        }
        return String.join("\n", intents.reversed());
    }

    public static Content buildUserContent(final String message) {
        return Content.builder()
                .role("user")
                .parts(List.of(Part.fromText(message)))
                .build();
    }

    public static List<Content> buildConfirmationContents(final Collection<Confirmation> confirmations) {
        final List<Content> contents = new ArrayList<>();
        for (final Confirmation confirmation : CollectionUtils.nullSafeList(confirmations)) {
            buildConfirmationEvent(confirmation.getConfirmationId(), confirmation.getConfirmed(), confirmation.getAnswer()).content().ifPresent(contents::add);
        }
        return contents;
    }

    private static Event buildConfirmationEvent(final String confirmationId, final Boolean confirmed, final String answer) {
        final ToolConfirmation toolConfirmation = ResponseUtils.buildToolConfirmation(confirmed, answer);
        final FunctionResponse functionResponse = FunctionResponse.builder()
                .id(confirmationId)
                .name(Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME)
                .response(JsonUtils.toMap(toolConfirmation))
                .build();
        return Event.builder()
                .author("user")
                .content(Content.builder()
                        .role("user")
                        .parts(List.of(Part.builder()
                                .functionResponse(functionResponse)
                                .build()))
                        .build())
                .build();
    }
}
