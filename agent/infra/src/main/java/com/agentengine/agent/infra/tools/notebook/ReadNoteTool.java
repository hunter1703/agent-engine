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
          Reads the text content of a note in a notebook you created or were granted. For small notes \
          (under 10,000 characters), the full content is returned immediately. For longer notes, content \
          is returned section-by-section. \
          Returns: { status: "success", content, chars_read, total_note_chars, has_more?, next_offset?, message? } or { error }.""",
          Map.of());

  private final NotebookService notebookService;

  public ReadNoteTool(final NotebookService notebookService) {
    super(DESCRIPTOR);
    this.notebookService = notebookService;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = Constants.ToolArgs.NOTEBOOK_ID,
              description = "The ID of the notebook to read from.")
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
          accessDeniedError(
              notebookService,
              "No such note: '%s', or you don't have access to its notebook."
                  .formatted(noteTitle)));
    }

    final String content = note.getContent() != null ? note.getContent() : "";
    final int totalChars = content.length();

    final int startOffset = offset != null ? Math.max(0, offset) : 0;

    if (offset != null || totalChars > DEFAULT_CHUNK_CHARS) {
      final int fromIndex = Math.min(startOffset, totalChars);
      final int toIndex = Math.min(fromIndex + DEFAULT_CHUNK_CHARS, totalChars);
      final String section = content.substring(fromIndex, toIndex);
      final boolean hasMore = toIndex < totalChars;

      final Map<String, Object> response = new LinkedHashMap<>();
      response.put(Constants.ToolStatus.STATUS, Constants.ToolStatus.SUCCESS);
      response.put("content", section);
      response.put("chars_read", section.length());
      response.put("total_note_chars", totalChars);
      response.put("has_more", hasMore);
      if (hasMore) {
        response.put("next_offset", toIndex);
        response.put(
            "message",
            "Showing characters %,d to %,d of %,d. Note is long; supply offset %,d to read the next section."
                .formatted(fromIndex, toIndex, totalChars, toIndex));
      }
      return ToolOutput.direct(response);
    }

    return ToolOutput.direct(
        Map.of(
            Constants.ToolStatus.STATUS,
            Constants.ToolStatus.SUCCESS,
            "content",
            content,
            "chars_read",
            totalChars,
            "total_note_chars",
            totalChars));
  }
}
