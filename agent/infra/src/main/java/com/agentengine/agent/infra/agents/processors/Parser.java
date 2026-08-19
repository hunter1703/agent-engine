package com.agentengine.agent.infra.agents.processors;

import com.agentengine.agent.infra.utils.ContentUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.BaseTool;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.LiveConnectConfig;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger LOG = LoggerFactory.getLogger(Parser.class);

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
        request.toBuilder().contents(sanitizeRequestContents(request.contents(), areToolsEnabled));
    if (StringUtils.isNotBlank(protocol)) {
      builder.appendInstructions(List.of(protocol));
    }
    final LlmRequest sanitizedRequest = builder.build();
    // adding check on areToolsEnabled as a hard stop on passing tools to model which don't support
    // tool calling because certain standard tools like HITL are automatically added based on
    // agent's config and not model's config (see AbstractAgentFactory)
    if (!areToolsEnabled) {
      // implementing like this because builder's set tool method is package private and hence we
      // can't use it to remove tools from the builder and hence from the llm request directly
      return new LlmRequest() {
        @Override
        public Optional<String> model() {
          return sanitizedRequest.model();
        }

        @Override
        public List<Content> contents() {
          return sanitizedRequest.contents();
        }

        @Override
        public Optional<GenerateContentConfig> config() {
          return sanitizedRequest.config();
        }

        @Override
        public LiveConnectConfig liveConnectConfig() {
          return sanitizedRequest.liveConnectConfig();
        }

        @Override
        public Map<String, BaseTool> tools() {
          return Map.of();
        }

        @Override
        public Builder toBuilder() {
          return builder;
        }
      };
    }
    return sanitizedRequest;
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
              .filter(
                  part ->
                      areToolsEnabled
                          || (part.functionCall().isEmpty() && part.functionResponse().isEmpty()))
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
    if (response == null || response.content().isEmpty() || response.partial().orElse(false)) {
      return response;
    }
    return response.toBuilder().content(parseTextContent(response.content().orElseThrow())).build();
  }

  private Content parseTextContent(final Content content) {
    String processedText = content.text();
    LOG.info("Parser.parseTextContent - initial text: '{}'", processedText);
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
        LOG.info("Parser.parseTextContent - extracted thought: '{}'", thoughtText);
        if (StringUtils.isNotBlank(thoughtText)) {
          thoughtParts.add(Part.builder().text(thoughtText).thought(true).build());
        }
      }
      processedText = thoughtMatcher.replaceAll("").trim();
      LOG.info("Parser.parseTextContent - text after stripping thoughts: '{}'", processedText);
    }

    final String finalAnswer = StringUtils.isBlank(processedText) ? "" : processedText.trim();
    LOG.info("Parser.parseTextContent - finalAnswer: '{}'", finalAnswer);
    final List<Part> allParts = new ArrayList<>(thoughtParts);
    if (StringUtils.isNotBlank(finalAnswer)) {
      allParts.add(
          Part.builder()
              .text(finalAnswer)
              .thought(CollectionUtils.isNotEmpty(toolCallParts))
              .build());
    }
    allParts.addAll(toolCallParts);
    return content.toBuilder().parts(allParts).build();
  }
}
