package com.agentengine.engine.agents.processors;

import com.agentengine.engine.api.utils.ContentUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.models.GeminiUtil;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes model content and tool-call payloads for the engine.
 *
 * <p>Responsibilities: - Parse text/thought tags into structured parts. - Split mixed parts (text +
 * tool payloads) into separate parts. - Drop tool call/response parts from partial responses. -
 * Emit violations when partial responses include tool payloads. - Convert tool call/response parts
 * into text when required for model input.
 *
 * <p>Ownership: content normalization, parsing, and protocol hygiene.
 */
public final class Parser {
  private static final Pattern THOUGHT_TAG_PATTERN =
      Pattern.compile("<thought>(.*?)</thought>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private final String protocol;
  private final boolean areToolsEnabled;

  public Parser(final String protocol, final boolean areToolsEnabled) {
    this.protocol = protocol == null ? "" : protocol;
    this.areToolsEnabled = areToolsEnabled;
  }

  public LlmRequest preProcess(final LlmRequest request) {
    final LlmRequest.Builder builder =
        request
            .toBuilder()
            .contents(sanitizeRequestContents(GeminiUtil.stripThoughts(request.contents()), areToolsEnabled));
    if (StringUtils.isNotBlank(protocol)) {
      builder.appendInstructions(List.of(protocol));
    }
    return builder.build();
  }

  private static List<Content> sanitizeRequestContents(
      final List<Content> contents, final boolean areToolsEnabled) {
    if (contents == null) {
      return List.of();
    }
    final List<Content> sanitized = new ArrayList<>(contents.size());
    for (final Content content : contents) {
      if (content == null) {
        continue;
      }
      final List<Part> parts =
          content.parts().orElse(List.of()).stream()
              .filter(part -> areToolsEnabled || (part.functionCall().isEmpty() && part.functionResponse().isEmpty()))
              .filter(part -> !ContentUtils.isEmptyPart(part))
              .toList();
      if (parts.isEmpty()) {
        continue;
      }
      sanitized.add(content.toBuilder().parts(parts).build());
    }
    return sanitized;
  }

  public LlmResponse postProcess(final LlmResponse response) {
    if (response == null || response.content().isEmpty()) {
      return response;
    }
    return response.toBuilder().content(parseTextContent(response.content().orElseThrow())).build();
  }

  private Content parseTextContent(final Content content) {
    String processedText = content.text();
    final List<Part> thoughtParts =
        new ArrayList<>(
            content.parts().orElse(List.of()).stream()
                .filter(part -> part.thought().orElse(false))
                .toList());
    final List<Part> toolCallParts = ContentUtils.getToolCallParts(content);

    if (StringUtils.isNotBlank(processedText)) {
      final Matcher thoughtMatcher = THOUGHT_TAG_PATTERN.matcher(processedText);
      while (thoughtMatcher.find()) {
        final String thoughtText = thoughtMatcher.group(1).trim();
        if (StringUtils.isNotBlank(thoughtText)) {
          thoughtParts.add(Part.builder().text(thoughtText).thought(true).build());
        }
      }
      processedText = thoughtMatcher.replaceAll("").trim();
    }

    final String finalAnswer = StringUtils.isBlank(processedText) ? "" : processedText.trim();
    final List<Part> allParts = new ArrayList<>(thoughtParts);
    if (StringUtils.isNotBlank(finalAnswer)) {
      allParts.add(Part.builder().text(finalAnswer).build());
    }
    allParts.addAll(toolCallParts);

    return content.toBuilder().parts(allParts).build();
  }
}
