package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.notebook.Note;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.agent.infra.utils.ResponseUtils;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Violation;
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
 * <p>{@code continuation} decides what happens next: {@code false} ends the run the same way a
 * normal final answer would; {@code true} calls {@link RunState#requestContinuation} so the run
 * loop issues another request with tools re-enabled, letting the model write further notes or keep
 * working.
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
    if (StringUtils.isBlank(text)) {
      LOG.info("Skipping create_note: no content produced for '{}'", pending.noteTitle());
      return Maybe.empty();
    }

    final Note note = new Note(pending.notebookId(), pending.noteTitle(), text);
    final Note existing = notesRepository.findById(note.getId());
    note.setVersion(existing == null ? 0 : existing.getVersion());
    notesRepository.save(note);
    final boolean overwritten = existing != null;
    LOG.info(
        "{} note notebook={} title={}",
        overwritten ? "Overwrote" : "Saved",
        pending.notebookId(),
        pending.noteTitle());

    final String overwriteWarning =
        overwritten
            ? " A note with this title already existed in this notebook — its previous content"
                + " was replaced."
            : "";
    final Violation violation =
        Violation.builder("note_continuation")
            .message(
                "Note '"
                    + pending.noteTitle()
                    + "' saved."
                    + overwriteWarning
                    + " Note content will not be directly visible to the user (unless the user reads the note). Continue with your task by calling tools (if needed) or giving final text answer that will be directly delivered and visible to the user. If you want to give the final answer, DO NOT copy the note content directly in the answer : the user has access to tools to read notes")
            .build();
    runState.requestContinuation(violation);
    return Maybe.empty();
  }
}
