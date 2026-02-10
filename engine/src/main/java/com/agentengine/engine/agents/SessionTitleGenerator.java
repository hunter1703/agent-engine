package com.agentengine.engine.agents;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.builders.model.OpenAIModelBuilder;
import com.agentengine.engine.utils.SessionUtils;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class SessionTitleGenerator {
  private static final Logger LOG = LoggerFactory.getLogger(SessionTitleGenerator.class);
  private static final int MAX_EVENTS = 30;
  private static final int MAX_TITLE_LENGTH = 80;

  private final BaseLlm titleGeneratorModel;

  public SessionTitleGenerator(OpenAIModelBuilder openAIModelBuilder) {
    final ModelConfig modelConfig = new ModelConfig();
    modelConfig.setModel("qwen2.5-1.5b-instruct-q5_k_m");
    this.titleGeneratorModel = openAIModelBuilder.build(modelConfig);
  }

  public Optional<String> generateTitle(final List<Event> events) {
    final List<Event> conversationEvents = SessionUtils.filterConversationEvents(events);
    if (conversationEvents.isEmpty()) {
      return Optional.empty();
    }
    final int startIndex = Math.max(0, conversationEvents.size() - MAX_EVENTS);
    final List<Event> recentEvents = conversationEvents.subList(startIndex, conversationEvents.size());
    final String transcript = SessionUtils.buildTranscript(recentEvents);
    if (StringUtils.isBlank(transcript)) {
      return Optional.empty();
    }
    final String prompt = buildPrompt(transcript);
    final Content promptContent = Content.builder().role("user").parts(Part.builder().text(prompt).build()).build();
    final LlmRequest request = LlmRequest.builder().contents(List.of(promptContent)).build();
    try {
      final LlmResponse response = titleGeneratorModel.generateContent(request, false).blockingFirst();
      final String rawTitle = response.content().map(Content::text).orElse(null);
      return Optional.ofNullable(sanitizeTitle(rawTitle));
    } catch (Exception e) {
      LOG.warn("Failed to generate session title", e);
      return Optional.empty();
    }
  }

  private static String buildPrompt(final String transcript) {
        return STR."""
        Generate a concise title (max 8 words) for this conversation. Respond with only the title.

        Conversation:
        \{transcript}
        """.strip();
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
