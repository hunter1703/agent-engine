package com.agentengine.runtime.session;

import com.agentengine.runtime.api.services.SessionHistoryService;
import com.agentengine.runtime.factories.model.ModelProvider;
import com.agentengine.runtime.utils.ContentUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.Cache;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.mongodb.infra.DefaultModelsConfig;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import com.google.adk.models.BaseLlm;
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

        boolean lastRunFound = false;
        // skips partial runs and generate title only on latest complete run
        for (final SessionEvent event : sessionEvents.reversed()) {
            if (event.getFinishReason() != null) {
                if (!lastRunFound) {
                    eventsToGenerateTitleOn.add(event);
                    lastRunFound = true;
                } else {
                    break;
                }
            } else if (lastRunFound) {
                eventsToGenerateTitleOn.add(event);
            }
        }

        final StringBuilder sb = new StringBuilder();

        for (final SessionEvent event : eventsToGenerateTitleOn.reversed()) {
            final String author = Objects.equals(Constants.AUTHOR_USER, event.getAuthor()) ? "USER" : "ASSISTANT";
            final Content content = event.getContent();
            if (content == null) {
                continue;
            }
            final List<Part> toolCallParts = ContentUtils.getToolCallParts(content);
            final List<Part> toolResponseParts = ContentUtils.getToolResponseParts(content);
            sb.append(author)
                    .append(": ")
                    .append(content.text())
                    .append("\n")
                    .append("Tool Calls : ")
                    .append(JsonUtils.toJson(
                            toolCallParts.stream().map(Part::functionCall).toList()))
                    .append("Tool Result : ")
                    .append(JsonUtils.toJson(
                            toolResponseParts.stream().map(Part::functionCall).toList()));

            sb.append("\n\n\n");
        }

        final BaseLlm model = modelProvider.acquire(titleGeneratorModelCache.get("model"));

        final LlmRequest request = LlmRequest.builder()
                .contents(List.of(INSTRUCTIONS, Content.fromParts(Part.fromText(sb.toString()))))
                .build();
        final LlmResponse response = model.generateContent(request, false).blockingLast();
        final Content responseContent = response.content().orElse(null);
        if (responseContent == null) {
            return null;
        }
        return responseContent.text();
    }
}
