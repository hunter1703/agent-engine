package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class CreateNoteTool extends AbstractNotebookTool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.CREATE_NOTE,
          "Stages a new note to be saved into a notebook, so other agents granted access to it "
              + "can read it instead of you having to relay its full text yourself. Call this "
              + "immediately before writing the note's content — not before a clarification or a "
              + "partial draft — then write that content as your very next message with nothing "
              + "else in between; it is saved automatically once you do. Set continuation=true if "
              + "you plan to write more notes or do more work afterward; leave it false (default) "
              + "if this note is your final output for this turn. "
              + "Returns: { status: \"pending\", message } — write the note's content next, or "
              + "{ error } if you don't have create access to this notebook.",
          Map.of());

  public CreateNoteTool() {
    super(DESCRIPTOR);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolSchema(
              name = Constants.ToolArgs.NOTEBOOK_ID,
              description = "The notebook to add this note to.")
          final String notebookId,
      @ToolSchema(
              name = Constants.ToolArgs.NOTE_TITLE,
              description = "Short title identifying this note within the notebook.")
          final String noteTitle,
      @ToolSchema(
              name = Constants.ToolArgs.CONTINUATION,
              description =
                  "If true, you'll get another turn (with tools available) after this note is "
                      + "saved. If false (default), this note's content is your final output for "
                      + "this turn.",
              optional = true)
          final Boolean continuation) {
    final boolean owner = NotebookUtils.isOwner(notebookId, toolContext.sessionId());
    final NotebookGrants grants = grantsOf(toolContext);
    if (!owner && !NotebookUtils.canCreate(grants, notebookId)) {
      return ToolOutput.direct(Map.of("error", "Not granted create access to this notebook."));
    }
    RunUtils.getRunState(toolContext.invocationContext())
        .startNote(notebookId, noteTitle, continuation != null && continuation);
    return ToolOutput.direct(
        Map.of(
            "status",
            "pending",
            "message",
            "Saving started — write this note's content as your very next message, with nothing "
                + "else first."));
  }
}
