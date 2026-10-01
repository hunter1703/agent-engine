package com.agentengine.agent.core.session;

import com.agentengine.agent.infra.session.SessionEventsRepository;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.common.Cache;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.factories.Model;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.common.cache.CacheBuilder;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Singleton
public class SessionTitleGenerator {
  private static final Content INSTRUCTIONS =
      Content.fromParts(
          Part.fromText(
              "INSTRUCTIONS : Generate a concise (maximum 10 words) title for the following conversation"));
  private static final int MAX_RUNS_TO_GENERATE_TITLE_ON = 10;
  private static final int EVENTS_PAGE_SIZE = 50;
  private final SessionEventsRepository sessionEventsRepository;
  private final Cache<String, String> titleGeneratorModelCache;
  private final ModelProvider modelProvider;

  public SessionTitleGenerator(
      final SessionEventsRepository sessionEventsRepository,
      final DefaultModelsRepository defaultModelsRepository,
      final ModelProvider modelProvider) {
    this.sessionEventsRepository = sessionEventsRepository;
    this.titleGeneratorModelCache =
        new Cache<>(
            CacheBuilder.newBuilder().maximumSize(1000),
            _ -> {
              final String fastModelId = defaultModelsRepository.getFastModelId();
              if (fastModelId == null) {
                throw new IllegalStateException(
                    "Default fast model not configured. Ensure infra configs are seeded before starting the runtime.");
              }
              return fastModelId;
            });
    this.modelProvider = modelProvider;
  }

  public String generateTitle(final String sessionId) {
    final List<SessionEvent> eventsToGenerateTitleOn = new ArrayList<>();

    int numRunsFound = 0;
    boolean enoughRuns = false;
    boolean lastPage = false;
    // newest first, a page at a time: skips partial runs and collects text events across the latest
    // complete runs, without reading the older history once those are found
    for (int offset = 0; !enoughRuns && !lastPage; offset += EVENTS_PAGE_SIZE) {
      final Page eventsPage = new Page(offset, EVENTS_PAGE_SIZE);
      final List<SessionEvent> page =
          sessionEventsRepository.getLatestCommittedEvents(sessionId, eventsPage).getItems();
      lastPage = page.size() < EVENTS_PAGE_SIZE;
      for (final SessionEvent event : page) {
        if (event.getFinishReason() != null) {
          if (numRunsFound >= MAX_RUNS_TO_GENERATE_TITLE_ON) {
            enoughRuns = true;
            break;
          }
          numRunsFound++;
        }
        final Content content = event.getContent();
        final String text = content == null ? null : content.text();
        if (StringUtils.isNotBlank(text)) {
          eventsToGenerateTitleOn.add(event);
        }
      }
    }

    if (eventsToGenerateTitleOn.isEmpty()) {
      return null;
    }
    final StringBuilder sb = new StringBuilder();

    for (final SessionEvent event : eventsToGenerateTitleOn.reversed()) {
      final String author =
          Objects.equals(Constants.AUTHOR_USER, event.getAuthor()) ? "USER" : "ASSISTANT";
      final Content content = event.getContent();
      sb.append(author).append(": ").append(content.text()).append("\n");
    }

    final Content content = Content.fromParts(Part.fromText(sb.toString()));
    final LlmRequest request =
        LlmRequest.builder().contents(List.of(INSTRUCTIONS, content)).build();
    try (RefCounted<Model.LLMModel> refCounted =
        modelProvider.get(titleGeneratorModelCache.get("model"))) {
      final LlmResponse response =
          refCounted.value().model().generateContent(request, false).blockingSingle();
      final Content responseContent = response.content().orElse(null);
      if (responseContent == null) {
        return null;
      }
      return responseContent.text();
    }
  }
}
