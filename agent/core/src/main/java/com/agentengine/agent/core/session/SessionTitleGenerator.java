package com.agentengine.agent.core.session;

import com.agentengine.agent.api.services.SessionHistoryService;
import com.agentengine.agent.infra.factories.model.ModelProvider;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.Cache;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.mongodb.infra.DefaultModelsConfig;
import com.agentengine.util.mongodb.infra.InfraConfigService;
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
    private static final Content INSTRUCTIONS = Content.fromParts(
            Part.fromText("INSTRUCTIONS : Generate a concise (maximum 10 words) title for the following conversation"));
    private static final int MAX_RUNS_TO_GENERATE_TITLE_ON = 10;
    private final SessionHistoryService sessionHistoryService;
    private final Cache<String, String> titleGeneratorModelCache;
    private final ModelProvider modelProvider;

    public SessionTitleGenerator(
            final SessionHistoryService sessionHistoryService,
            final InfraConfigService infraConfigService,
            final ModelProvider modelProvider) {
        this.sessionHistoryService = sessionHistoryService;
        this.titleGeneratorModelCache = new Cache<>(CacheBuilder.newBuilder().maximumSize(1000), _ -> {
            final DefaultModelsConfig defaultModelConfig =
                    infraConfigService.findById(DefaultModelsConfig.CATEGORY, DefaultModelsConfig.CONFIG_ID);
            if (defaultModelConfig == null) {
                throw new IllegalStateException(
                        "Default models config not found. Ensure infra configs are seeded before starting the runtime.");
            }
            return defaultModelConfig.getTitleModelId();
        });
        this.modelProvider = modelProvider;
    }

    public String generateTitle(final String sessionId) {
        final List<SessionEvent> sessionEvents =
                CollectionUtils.nullSafeList(sessionHistoryService.getSessionEvents(sessionId));
        if (CollectionUtils.isEmpty(sessionEvents)) {
            return null;
        }
        final List<SessionEvent> eventsToGenerateTitleOn = new ArrayList<>();

        int numRunsFound = 0;
        // skips partial runs and generate title only on latest complete run
        for (final SessionEvent event : sessionEvents.reversed()) {
            if (event.getFinishReason() != null) {
                if (numRunsFound >= MAX_RUNS_TO_GENERATE_TITLE_ON) {
                    break;
                } else {
                    numRunsFound++;
                }
            }
            final Content content = event.getContent();
            final String text = content == null ? null : content.text();
            if (StringUtils.isBlank(text)) {
                continue;
            }
            eventsToGenerateTitleOn.add(event);
        }

        final StringBuilder sb = new StringBuilder();

        for (final SessionEvent event : eventsToGenerateTitleOn.reversed()) {
            final String author = Objects.equals(Constants.AUTHOR_USER, event.getAuthor()) ? "USER" : "ASSISTANT";
            final Content content = event.getContent();
            sb.append(author).append(": ").append(content.text()).append("\n");
        }

        final Content content = Content.fromParts(Part.fromText(sb.toString()));
        final LlmRequest request =
                LlmRequest.builder().contents(List.of(INSTRUCTIONS, content)).build();
        final LlmResponse response = modelProvider
                .invokeAcquiring(titleGeneratorModelCache.get("model"), model -> model.generateContent(request, false))
                .blockingSingle();
        final Content responseContent = response.content().orElse(null);
        if (responseContent == null) {
            return null;
        }
        return responseContent.text();
    }
}
