package com.agentengine.agent.infra.tools.notebook;

import com.agentengine.agent.infra.notebook.Note;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.google.adk.tools.ToolContext;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReadNoteTool extends AbstractNotebookTool {
  public static final int DEFAULT_CHUNK_CHARS = 10_000;

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.READ_NOTE,
          """
          Reads a note. A long note is returned in sections of 10,000 characters.
          Returns: { content }, or for a long note or a read from an offset { content, total_chars, next_offset? } where next_offset is present while more of the note remains, or { error }.""",
          Map.of());

  public ReadNoteTool(final NotebookService notebookService) {
    super(DESCRIPTOR, notebookService);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = Constants.ToolArgs.NOTEBOOK_ID,
              description =
                  "The exact ID of the notebook to read from, as shown in your notebook list.")
          final String notebookId,
      @ToolArg(name = Constants.ToolArgs.NOTE_TITLE, description = "The title of the note to read.")
          final String noteTitle,
      @ToolArg(
              name = "offset",
              description =
                  """
                  0-indexed character offset to begin reading from. Defaults to 0. When reading a long note
                  section by section, pass the next_offset returned by the previous read.""",
              optional = true)
          final Integer offset) {
    final Note note = notebookService.getNote(notebookId, noteTitle);
    if (note == null) {
      return ToolOutput.direct(
          accessDeniedError("No such note: '%s', or you don't have access to its notebook."
                  .formatted(noteTitle)));
    }

    final String content = note.getContent() != null ? note.getContent() : "";
    final int totalChars = content.length();
    final int fromIndex = offset != null ? Math.min(Math.max(0, offset), totalChars) : 0;
    final int toIndex = Math.min(fromIndex + DEFAULT_CHUNK_CHARS, totalChars);
    if (offset == null && toIndex == totalChars) {
      return ToolOutput.direct(Map.of("content", content));
    }

    final Map<String, Object> response = new LinkedHashMap<>();
    response.put("content", content.substring(fromIndex, toIndex));
    response.put("total_chars", totalChars);
    if (toIndex < totalChars) {
      response.put("next_offset", toIndex);
    }
    return ToolOutput.direct(response);
  }
}
