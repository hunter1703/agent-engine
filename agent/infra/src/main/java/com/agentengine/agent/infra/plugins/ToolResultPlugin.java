package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.tools.knowledge.SearchKnowledgeTool;
import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.MediaDescription;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.knowledge.api.services.MediaService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.ContentUtils;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.agentengine.util.common.utils.Utils;
import com.google.adk.events.Event;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import io.reactivex.rxjava3.core.Maybe;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Shapes what the model sees of tool results.
 *
 * <p><b>Timing.</b> Tells the model when each tool call completed and how long it took, so it knows
 * time has passed between its steps.
 *
 * <p>A result gains {@code completedAt}, the time it was produced in the zone the user's message
 * was stamped in, written with its UTC offset so every result names its zone however far back in
 * the history it is, and {@code timeTaken}, the time from the model's event making the call to the
 * result, pauses included: a call that waited for a child session or a user answer reports
 * the whole wait. A result that already has a field of one of these names keeps it, and an empty
 * result stays empty, since an empty result is how a tool that raises an interrupt answers.
 *
 * <p><b>Attached media.</b> A result marked {@link SearchKnowledgeTool#ATTACHED} names an image,
 * audio or other binary knowledge item that the model should look at. Tool results carry text
 * only, so the item is added to each request as a user message right after that result, for as
 * long as the run lasts. History keeps only the result, never the bytes. A model that cannot take
 * media gets the item's description instead.
 */
public final class ToolResultPlugin extends BasePlugin {
  private static final Logger LOG = LoggerFactory.getLogger(ToolResultPlugin.class);
  public static final String COMPLETED_AT = "completedAt";
  public static final String TIME_TAKEN = "timeTaken";

  private final KnowledgeService knowledgeService;
  private final FileService fileService;
  private final MediaService mediaService;
  private final ModelProvider modelProvider;
  private final Cache<String, byte[]> knowledgeIdVsBytes =
      CacheBuilder.newBuilder()
          .maximumWeight(30_000_000)
          .weigher((String _, byte[] bytes) -> bytes.length)
          .expireAfterWrite(10, TimeUnit.MINUTES)
          .build();
  private final Cache<String, String> knowledgeIdVsDescription =
      CacheBuilder.newBuilder().maximumSize(100).expireAfterWrite(10, TimeUnit.MINUTES).build();

  public ToolResultPlugin(
      final KnowledgeService knowledgeService,
      final FileService fileService,
      final MediaService mediaService,
      final ModelProvider modelProvider) {
    super("tool_result_plugin");
    this.knowledgeService = knowledgeService;
    this.fileService = fileService;
    this.mediaService = mediaService;
    this.modelProvider = modelProvider;
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder requestBuilder) {
    final List<Content> contents = requestBuilder.build().contents();
    final int userMessageIndex =
        CollectionUtils.findLastIndexAfter(contents, -1, ContentUtils::isUserSentMessage);
    if (userMessageIndex < 0) {
      return Maybe.empty();
    }
    final boolean vision = isVisionModel(callbackContext.invocationContext());
    final List<Content> updated = new ArrayList<>(contents.subList(0, userMessageIndex + 1));
    boolean changed = false;
    for (final Content content : contents.subList(userMessageIndex + 1, contents.size())) {
      final List<Part> parts = new ArrayList<>(content.parts().orElse(List.of()));
      for (final FunctionResponse response :
          ContentUtils.getFunctionResponses(content, Constants.ToolNames.SEARCH_KNOWLEDGE)) {
        if (Boolean.TRUE.equals(response.response().orElse(Map.of()).get(SearchKnowledgeTool.ATTACHED))) {
          parts.addAll(attachmentParts(response.response().orElseThrow(), vision));
        }
      }
      final boolean attached = parts.size() > content.parts().orElse(List.of()).size();
      updated.add(attached ? content.toBuilder().parts(parts).build() : content);
      changed |= attached;
    }
    if (changed) {
      requestBuilder.contents(updated);
    }
    return Maybe.empty();
  }

  @Override
  public Maybe<Map<String, Object>> afterToolCallback(
      final BaseTool tool,
      final Map<String, Object> args,
      final ToolContext toolContext,
      final Map<String, Object> result) {
    if (result == null || result.isEmpty()) {
      return Maybe.empty();
    }
    final Instant completedAt = Instant.now();
    final List<Event> events = toolContext.invocationContext().session().events();
    final Instant calledAt =
        toolContext
            .functionCallId()
            .flatMap(callId -> callEvent(events, callId))
            .map(event -> Instant.ofEpochMilli(event.timestamp()))
            .orElse(completedAt);
    final ZoneId zone =
        Utils.zoneOf(
            CollectionUtils.getStringValueFromMap(toolContext.state(), EventUtils.TIME_ZONE_KEY));

    final Map<String, Object> timed = new LinkedHashMap<>(result);
    timed.putIfAbsent(
        COMPLETED_AT,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            completedAt.truncatedTo(ChronoUnit.SECONDS).atZone(zone)));
    timed.putIfAbsent(TIME_TAKEN, timeTaken(Duration.between(calledAt, completedAt)));
    return Maybe.just(timed);
  }

  /** {@code duration} as seconds below a minute, e.g. {@code 4.2s}, else as {@code 1m 37s}. */
  private static String timeTaken(final Duration duration) {
    final long millis = Math.max(0, duration.toMillis());
    if (millis < 60_000) {
      return String.format(Locale.ENGLISH, "%.1fs", millis / 100 / 10.0);
    }
    final long totalSeconds = millis / 1000;
    final long hours = totalSeconds / 3600;
    final long minutes = totalSeconds % 3600 / 60;
    final long seconds = totalSeconds % 60;
    return hours > 0
        ? "%dh %dm %ds".formatted(hours, minutes, seconds)
        : "%dm %ds".formatted(minutes, seconds);
  }

  private boolean isVisionModel(final InvocationContext context) {
    final BaseAgentConfig config = SessionUtils.agentConfig(context);
    return config != null && modelProvider.isVisionEnabled(config.getModelId());
  }

  /** The parts that show the item an attached result names, for the content holding the result. */
  private List<Part> attachmentParts(final Map<String, Object> result, final boolean vision) {
    final String knowledgeId =
        CollectionUtils.getStringValueFromMap(result, Constants.ToolArgs.KNOWLEDGE_ID);
    final String mimeType = CollectionUtils.getStringValueFromMap(result, "mime_type");
    final String name = "Knowledge item '%s' (%s)".formatted(knowledgeId, mimeType);
    if (vision) {
      final byte[] bytes = bytesOf(knowledgeId);
      if (bytes != null) {
        return List.of(
            Part.fromText(ContentUtils.systemNotice(name + ", from the result above:")),
            Part.fromBytes(bytes, mimeType));
      }
    } else {
      final String description = descriptionOf(knowledgeId);
      if (description != null) {
        return List.of(
            Part.fromText(
                ContentUtils.systemNotice(
                    name + ", from the result above, which you cannot view. Its description: "
                        + description)));
      }
    }
    return List.of(Part.fromText(ContentUtils.systemNotice(name + " could not be shown.")));
  }

  private byte[] bytesOf(final String knowledgeId) {
    try {
      return knowledgeIdVsBytes.get(
          knowledgeId,
          () -> {
            final Knowledge knowledge = knowledgeService.findById(knowledgeId);
            try (InputStream content = fileService.getContent(knowledge.getFileDetails())) {
              final byte[] bytes = content.readNBytes(SearchKnowledgeTool.MAX_ATTACHED_BYTES + 1);
              if (bytes.length > SearchKnowledgeTool.MAX_ATTACHED_BYTES) {
                throw new IllegalStateException("Too large to show: " + knowledgeId);
              }
              return bytes;
            }
          });
    } catch (final Exception unavailable) {
      LOG.warn("Could not load knowledge item {} to show it", knowledgeId, unavailable);
      return null;
    }
  }

  private String descriptionOf(final String knowledgeId) {
    try {
      return knowledgeIdVsDescription.get(
          knowledgeId,
          () -> {
            final MediaDescription media =
                mediaService.describe(knowledgeService.findById(knowledgeId).getFileDetails());
            return StringUtils.joinNonBlank(List.of(media.description(), media.transcript()));
          });
    } catch (final Exception unavailable) {
      LOG.warn("Could not describe knowledge item {}", knowledgeId, unavailable);
      return null;
    }
  }

  private static Optional<Event> callEvent(final List<Event> events, final String callId) {
    return events.reversed().stream()
        .filter(
            event ->
                event.functionCalls().stream()
                    .anyMatch(call -> call.id().filter(callId::equals).isPresent()))
        .findFirst();
  }
}
