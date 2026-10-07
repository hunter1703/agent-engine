package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.tools.notebook.CreateOrUpdateNoteTool;
import com.agentengine.agent.infra.utils.*;
import com.agentengine.util.agents.beans.Signal;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.*;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forces the model's very next request tool-free and thinking-disabled once {@code create_note} (a
 * normal, always-declared tool — see {@link CreateOrUpdateNoteTool}) has staged a note, so its only
 * option is to write the note's content as plain text; {@link #afterModelCallback} then persists
 * it.
 *
 * <p>Once the note is persisted, {@link #afterModelCallback} always queues a signal that requires
 * continuation, so the run loop issues another request with tools re-enabled, letting the model
 * write further notes or keep working.
 */
public final class NotebookPlugin extends BasePlugin {
  private static final Logger LOG = LoggerFactory.getLogger(NotebookPlugin.class);
  private static final String NAME = "notebook_plugin";

  private final NotebookService notebookService;
  private final Set<String> agentsWithNotebook;

  public NotebookPlugin(
      final NotebookService notebookService, final Set<String> agentsWithNotebook) {
    super(NAME);
    this.notebookService = notebookService;
    this.agentsWithNotebook = agentsWithNotebook;
  }

  /** Tells an agent with notebook tools, once per run, which notebooks and notes it can reach. */
  @Override
  public Maybe<Content> beforeAgentCallback(
      final BaseAgent agent, final CallbackContext callbackContext) {
    final InvocationContext invocationContext = callbackContext.invocationContext();
    if (agentsWithNotebook.contains(agent.name())) {
      SessionUtils.getSessionState(invocationContext).syncNotebookReminder(notebookService);
    }
    return Maybe.empty();
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder llmRequestBuilder) {
    if (!agentsWithNotebook.contains(callbackContext.agentName())) {
      return Maybe.empty();
    }
    final InvocationContext invocationContext = callbackContext.invocationContext();
    if (RunUtils.getRunState(invocationContext).isNoteStarted()) {
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
    final SessionState sessionState = SessionUtils.getSessionState(invocationContext);
    final RunState runState = sessionState.runState();
    if (!runState.isNoteStarted() || !ResponseUtils.isFinalAnswer(response)) {
      return Maybe.empty();
    }

    final RunState.PendingNote pending = runState.finishNote();
    final String text = response.content().map(Content::text).map(String::trim).orElse("");
    final String noteTitle = pending.noteTitle();
    if (StringUtils.isBlank(text)) {
      LOG.debug("Skipping create_note: no content produced for '{}'", noteTitle);
      return Maybe.empty();
    }

    final String notebookId = pending.notebookId();
    notebookService.saveNote(notebookId, noteTitle, text);
    sessionState.syncNotebookReminder(notebookService);
    LOG.info("Created or updated note notebook={} title={}", notebookId, noteTitle);
    final String message =
        """
            Note '%s' saved. Its content now lives in the shared notebook, where anyone with access can read it. Your reply and the note are separate channels : the note carries the content and, the reply carries whatever else you want to communicate. Continue your task, or give your final answer if you're done.
            """
            .formatted(noteTitle);
    runState.addSignal(
        callbackContext,
        new Signal<>(
            "note_saved_" + NotebookUtils.noteId(notebookId, noteTitle), message, true));
    return Maybe.empty();
  }
}
