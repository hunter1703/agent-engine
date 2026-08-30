package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.agent.infra.notebook.Note;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.agent.infra.utils.*;
import com.agentengine.util.agents.beans.Signal;
import com.agentengine.util.common.StringUtils;
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
 * normal, always-declared tool — see {@link
 * com.agentengine.agent.infra.tools.notebook.CreateNoteTool}) has staged a note, so its only option
 * is to write the note's content as plain text; {@link #afterModelCallback} then persists it.
 *
 * <p>Once the note is persisted, {@link #afterModelCallback} always queues a signal that requires
 * continuation, so the run loop issues another request with tools re-enabled, letting the model
 * write further notes or keep working.
 */
public final class NotebookPlugin extends BasePlugin {
  private static final Logger LOG = LoggerFactory.getLogger(NotebookPlugin.class);
  private static final String NAME = "notebook_plugin";

  private final NotesRepository notesRepository;
  private final Set<String> agentsWithNotebook;

  public NotebookPlugin(
      final NotesRepository notesRepository, final Set<String> agentsWithNotebook) {
    super(NAME);
    this.notesRepository = notesRepository;
    this.agentsWithNotebook = agentsWithNotebook;
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
    final RunState runState = SessionUtils.getSessionState(invocationContext).runState();
    if (!runState.isNoteStarted() || !ResponseUtils.isFinalAnswer(response)) {
      return Maybe.empty();
    }

    final RunState.PendingNote pending = runState.finishNote();
    final String text = response.content().map(Content::text).map(String::trim).orElse("");
    final String noteTitle = pending.noteTitle();
    if (StringUtils.isBlank(text)) {
      LOG.info("Skipping create_note: no content produced for '{}'", noteTitle);
      return Maybe.empty();
    }

    final String notebookId = pending.notebookId();
    final Note note = new Note(notebookId, noteTitle, text);
    final Note existing = notesRepository.findById(note.getId());
    note.setVersion(existing == null ? 0 : existing.getVersion());
    notesRepository.save(note);
    LOG.info("Created or updated note notebook={} title={}", notebookId, noteTitle);
    final String message =
        """
            Note '%s' saved. Your caller doesn't see its content automatically — they can read \
            it themselves if they need to, so don't paste it into your reply. \
            Continue your task, or give your final answer now.
            """
            .formatted(noteTitle);
    runState.addSignal(
        callbackContext,
        new Signal<>(
            "note_" + NotebookUtils.noteId(notebookId, noteTitle) + "_saved", message, true));
    return Maybe.empty();
  }
}
