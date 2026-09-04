package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.ContentUtils;
import com.agentengine.agent.infra.utils.ResponseUtils;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.service.CloudStorageService;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.*;
import io.reactivex.rxjava3.core.Maybe;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-cutting "save the current answer as a versioned artifact" capability, registered without a
 * dedicated {@code Tool} class — {@code save_answer}'s declaration is a {@link FunctionTool}
 * wrapping {@link #saveAnswer}, built once per plugin instance and reused across every {@link
 * #beforeModelCallback} call, the same way {@code com.google.adk.flows.llmflows.AgentTransfer}
 * registers {@code transfer_to_agent} (though that one rebuilds its tool on every call).
 *
 * <p>The tool call itself can't perform the real persistence — the answer's text doesn't exist yet
 * at the moment {@code save_answer} is called. {@link #saveAnswer} just stages intent on {@link
 * RunState} (answer mode); {@link #beforeModelCallback} then forces the very next request to be
 * tool-free and thinking-disabled, so the model's only option is to write that answer as plain
 * text; {@link #afterModelCallback} performs the actual persistence once that response arrives.
 * {@link #beforeModelCallback} also strips resolved {@code save_answer} call/response pairs from
 * history on every request, so the model never sees a "write, then call save_answer" pattern in its
 * own past turns it might imitate on turns that aren't actually final.
 *
 * <p>{@code minSaveTokensByAgentName} mirrors {@link
 * com.agentengine.agent.infra.plugins.GuardrailPlugin}/{@link ContextManagementPlugin}'s per-agent
 * config map, built once in {@code RunnerFactory.buildPlugins} from each agent's own {@code
 * save_answer} tool config. An agent absent from this map never has {@code save_answer} offered at
 * all — every callback here is a no-op for it.
 */
public final class SaveAnswerPlugin extends BasePlugin {
  private static final Logger LOG = LoggerFactory.getLogger(SaveAnswerPlugin.class);
  private static final String NAME = "save_answer_plugin";

  public static final String ANSWER_ID = "answer";

  private static final Method SAVE_ANSWER_METHOD;

  static {
    try {
      SAVE_ANSWER_METHOD =
          SaveAnswerPlugin.class.getMethod("saveAnswer", String.class, ToolContext.class);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  private final CloudStorageService cloudStorageService;
  private final Map<String, Long> agentVsSaveAnswerThreshold;
  private final FunctionTool saveAnswerTool;

  public SaveAnswerPlugin(
      final CloudStorageService cloudStorageService,
      final Map<String, Long> agentVsSaveAnswerThreshold) {
    super(NAME);
    this.cloudStorageService = cloudStorageService;
    this.agentVsSaveAnswerThreshold = Map.copyOf(agentVsSaveAnswerThreshold);
    this.saveAnswerTool = FunctionTool.create(this, SAVE_ANSWER_METHOD);
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder llmRequestBuilder) {
    if (!agentVsSaveAnswerThreshold.containsKey(callbackContext.agentName())) {
      return Maybe.empty();
    }
    final InvocationContext invocationContext = callbackContext.invocationContext();

    llmRequestBuilder.contents(stripSaveAnswerParts(llmRequestBuilder.build().contents()));

    saveAnswerTool.processLlmRequest(
        llmRequestBuilder, ToolContext.builder(invocationContext).build());

    if (RunUtils.getRunState(invocationContext).isInAnswerMode()) {
      final GenerateContentConfig config =
          llmRequestBuilder
              .build()
              .config()
              .orElseGet(() -> GenerateContentConfig.builder().build())
              .toBuilder()
              .toolConfig(
                  ToolConfig.builder()
                      .functionCallingConfig(
                          FunctionCallingConfig.builder()
                              .mode(
                                  new FunctionCallingConfigMode(
                                      FunctionCallingConfigMode.Known.NONE))
                              .build())
                      .build())
              .thinkingConfig(ThinkingConfig.builder().thinkingBudget(0).build())
              .build();
      llmRequestBuilder.config(config);
    }
    return Maybe.empty();
  }

  @Override
  public Maybe<LlmResponse> afterModelCallback(
      final CallbackContext callbackContext, final LlmResponse response) {
    if (response.partial().orElse(false)) {
      return Maybe.empty();
    }
    final InvocationContext invocationContext = callbackContext.invocationContext();
    final RunState runState = SessionUtils.getSessionState(invocationContext).runState();
    if (!runState.isInAnswerMode() || !ResponseUtils.isFinalAnswer(response)) {
      // Not in answer mode: no-op. In answer mode but not a plain final answer: beforeModelCallback
      // forced this request tool-free, so this shouldn't happen — leave answer mode active so a
      // later turn gets another chance instead of silently losing the save.
      return Maybe.empty();
    }

    final RunState.PendingAnswer pending = runState.consumeAnswerMode();
    final String text = response.content().map(Content::text).map(String::trim).orElse("");
    if (StringUtils.isBlank(text) || StringUtils.estimateTokens(text) < pending.minSaveTokens()) {
      LOG.debug("Skipping save_answer: answer below the {}-token minimum", pending.minSaveTokens());
      return Maybe.empty();
    }

    final String agentId = invocationContext.agent().name();
    final String userId = invocationContext.session().userId();
    final String sessionId = invocationContext.session().id();
    final String prefix = userId + "/" + agentId + "/" + sessionId + "/" + ANSWER_ID + "/";

    final int version =
        cloudStorageService.list(prefix).stream()
                .map(key -> key.substring(key.lastIndexOf('/') + 1))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(-1)
            + 1;

    final byte[] data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    cloudStorageService.upload(
        prefix + version,
        ANSWER_ID,
        new java.io.ByteArrayInputStream(data),
        data.length,
        "text/plain",
        Map.of("saveMessage", pending.saveMessage()));

    LOG.info("Saved answer id={} version={}", ANSWER_ID, version);
    return Maybe.empty();
  }

  @Schema(
      name = Constants.ToolNames.SAVE_ANSWER,
      description =
          "Stages your answer to be saved as a versioned artifact, so other agents can reference it "
              + "instead of you having to relay its full text yourself. There is only one such "
              + "answer: each save creates a new version of it, and the latest version is what other "
              + "agents see. Call this immediately before writing your final answer — not before a "
              + "clarification, a disagreement, or a work-in-progress draft — then write that answer "
              + "as your very next message with nothing else in between; it is saved automatically "
              + "once you do. Calling this on the wrong turn causes whatever you write next to "
              + "overwrite the real version with something that isn't the actual answer. "
              + "Returns: { status: \"pending\", message } — write your final answer next.")
  public Map<String, Object> saveAnswer(
      @Schema(
              name = "saveMessage",
              description =
                  "A short summary of what this answer contains, in absolute terms (e.g. "
                      + "\"the finalized three-act outline\") — not what changed or was updated "
                      + "relative to the previous version.")
          final String saveMessage,
      @Schema(name = "toolContext", optional = true) final ToolContext toolContext) {
    final long minSaveTokens = agentVsSaveAnswerThreshold.getOrDefault(toolContext.agentName(), 0L);
    RunUtils.getRunState(toolContext.invocationContext())
        .enterAnswerMode(saveMessage, minSaveTokens);
    return Map.of(
        "status",
        "pending",
        "message",
        "Saving started — write your final answer as your very next message, with nothing "
            + "else first.");
  }

  private static List<Content> stripSaveAnswerParts(final List<Content> contents) {
    final List<Content> filtered = new ArrayList<>();
    for (final Content content : CollectionUtils.nullSafeList(contents)) {
      final List<Part> parts = content.parts().orElse(List.of());
      final List<Part> kept =
          parts.stream()
              .filter(
                  part ->
                      !ContentUtils.isFunctionCall(part, Constants.ToolNames.SAVE_ANSWER)
                          && !ContentUtils.isFunctionResponse(
                              part, Constants.ToolNames.SAVE_ANSWER))
              .toList();
      if (!kept.isEmpty()) {
        filtered.add(content.toBuilder().parts(kept).build());
      }
    }
    return filtered;
  }
}
