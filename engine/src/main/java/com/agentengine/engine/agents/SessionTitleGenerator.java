package com.agentengine.engine.agents;

import static com.agentengine.engine.infra.TitleConfig.TYPE;

import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.infra.TitleConfig;
import com.agentengine.engine.repository.InfraMongoRepository;
import com.agentengine.engine.utils.LazyLoader;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class SessionTitleGenerator {
  private static final Logger LOG = LoggerFactory.getLogger(SessionTitleGenerator.class);
  private static final int MAX_EVENTS = 30;
  private static final int MAX_TITLE_LENGTH = 80;
  private final LazyLoader<BaseLlm> titleGeneratorModelLoader;

  public SessionTitleGenerator(
      InfraMongoRepository infraMongoRepository, ModelProvider modelProvider) {
    this.titleGeneratorModelLoader =
        new LazyLoader<>(
            () -> {
              final AgentModelConfig agentModelConfig = new AgentModelConfig();
              agentModelConfig.setRole("title_generator");
              agentModelConfig.setSystemPrompt(
                  "You are a helpful assistant that generates concise and descriptive titles for conversations based on their content. The title should capture the main topic or theme of the conversation in a clear and engaging way.");
              final TitleConfig config = infraMongoRepository.findOneByType(TYPE);
              if (config == null || StringUtils.isBlank(config.getModelId())) {
                LOG.warn("Title generator configuration not found or incomplete. Type: {}", TYPE);
                return null;
              }
              agentModelConfig.setModelId(config.getModelId());
              return modelProvider.get(agentModelConfig);
            });
  }

  public static List<Event> filterConversationEvents(final List<Event> events) {
    if (events == null || events.isEmpty()) {
      return List.of();
    }
    final List<Event> filtered = new ArrayList<>();
    for (final Event event : events) {
      if (hasConversationContent(event)) {
        filtered.add(event);
      }
    }
    return filtered;
  }

  public static boolean hasConversationContent(final Event event) {
    if (event == null) {
      return false;
    }
    return event.content().flatMap(Content::parts).map(parts -> !parts.isEmpty()).orElse(false);
  }

  public static String buildTranscript(final List<Event> events) {
    if (events == null || events.isEmpty()) {
      return "";
    }
    final StringBuilder builder = new StringBuilder();
    for (final Event event : events) {
      final String text = extractText(event);
      if (StringUtils.isBlank(text)) {
        continue;
      }
      final String author = StringUtils.isBlank(event.author()) ? "assistant" : event.author();
      builder.append(author).append(": ").append(text.trim()).append("\n");
    }
    return builder.toString().trim();
  }

  private static String extractText(final Event event) {
    if (event == null) {
      return null;
    }
    final Content content = event.content().orElse(null);
    if (content == null) {
      return null;
    }
    final String text = content.text();
    if (StringUtils.isNotBlank(text)) {
      return text;
    }
    return summarizeParts(content.parts().orElse(List.of()));
  }

  private static String summarizeParts(final List<Part> parts) {
    if (parts == null || parts.isEmpty()) {
      return null;
    }
    final StringBuilder builder = new StringBuilder();
    for (final Part part : parts) {
      part.functionCall()
          .ifPresent(
              call ->
                  builder.append("Tool call: ").append(call.name().orElse("tool")).append('\n'));
      part.functionResponse()
          .ifPresent(
              response ->
                  builder
                      .append("Tool result: ")
                      .append(response.name().orElse("tool"))
                      .append('\n'));
    }
    final String summary = builder.toString().trim();
    return summary.isEmpty() ? null : summary;
  }

  public Optional<String> generateTitle(final List<Event> events) {
    final BaseLlm titleGeneratorModel = titleGeneratorModelLoader.getInstance();
    if (titleGeneratorModel == null) {
      return Optional.empty();
    }
    final List<Event> conversationEvents = filterConversationEvents(events);
    if (conversationEvents.isEmpty()) {
      return Optional.empty();
    }
    final int startIndex = Math.max(0, conversationEvents.size() - MAX_EVENTS);
    final List<Event> recentEvents =
        conversationEvents.subList(startIndex, conversationEvents.size());
    final String transcript = buildTranscript(recentEvents);
    if (StringUtils.isBlank(transcript)) {
      return Optional.empty();
    }
    final String prompt = buildPrompt(transcript);
    final Content promptContent =
        Content.builder().role("user").parts(Part.builder().text(prompt).build()).build();
    final LlmRequest request = LlmRequest.builder().contents(List.of(promptContent)).build();
    try {
      final LlmResponse response =
          titleGeneratorModel.generateContent(request, false).blockingFirst();
      final String rawTitle = response.content().map(Content::text).orElse(null);
      return Optional.ofNullable(sanitizeTitle(rawTitle));
    } catch (Exception e) {
      LOG.warn("Failed to generate session title", e);
      return Optional.empty();
    }
  }

  private static String buildPrompt(final String transcript) {
    return String.format(
            """
        Generate a concise title (max 8 words) for this conversation. Respond with only the title.

        Conversation:
        %s
        """,
            transcript)
        .strip();
  }

  private static String sanitizeTitle(final String title) {
    if (StringUtils.isBlank(title)) {
      return null;
    }
    String sanitized = title.trim().replaceAll("\\s+", " ");
    if ((sanitized.startsWith("\"") && sanitized.endsWith("\""))
        || (sanitized.startsWith("'") && sanitized.endsWith("'"))) {
      sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
    }
    if (sanitized.length() > MAX_TITLE_LENGTH) {
      sanitized = sanitized.substring(0, MAX_TITLE_LENGTH).trim();
    }
    return StringUtils.isBlank(sanitized) ? null : sanitized;
  }
}
