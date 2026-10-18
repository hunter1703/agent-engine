package com.agentengine.agent.infra.notebook;

import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.util.common.utils.StringUtils;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ScratchpadNotebook extends Notebook {

  public static final String ID = "scratchpad";
  public static final String DEFAULT_NAME = "Scratchpad";
  public static final String DEFAULT_DESCRIPTION =
      "Optional private working memory for this session. Use this only when you need to retain"
          + " intermediate findings, calculations, or structured data across long multi-step"
          + " investigations or context compaction. Do not record transient observations or"
          + " single-step findings that are already available in immediate context.";

  private final Map<String, Note> titleVsNote = new LinkedHashMap<>();

  public ScratchpadNotebook(final String sessionId) {
    super(sessionId, DEFAULT_NAME, DEFAULT_DESCRIPTION, NotebookType.SCRATCHPAD);
    setId(ID);
  }

  public Note getNote(final String noteTitle) {
    if (StringUtils.isBlank(noteTitle)) {
      return null;
    }
    return titleVsNote.get(NotebookUtils.sanitize(noteTitle));
  }

  public Note saveNote(final String noteTitle, final String content) {
    final Note note = new Note(getId(), noteTitle, content);
    titleVsNote.put(NotebookUtils.sanitize(noteTitle), note);
    return note;
  }

  public Note appendNote(final String noteTitle, final String contentToAppend) {
    final String key = NotebookUtils.sanitize(noteTitle);
    final Note existing = titleVsNote.get(key);
    final String newContent;
    if (existing == null || StringUtils.isBlank(existing.getContent())) {
      newContent = contentToAppend;
    } else {
      newContent = existing.getContent() + "\n\n" + contentToAppend;
    }
    final Note note = new Note(getId(), noteTitle, newContent);
    titleVsNote.put(key, note);
    return note;
  }

  public boolean deleteNote(final String noteTitle) {
    if (StringUtils.isBlank(noteTitle)) {
      return false;
    }
    return titleVsNote.remove(NotebookUtils.sanitize(noteTitle)) != null;
  }

  public Collection<Note> notes() {
    return Collections.unmodifiableCollection(titleVsNote.values());
  }

  public Collection<String> noteTitles() {
    return titleVsNote.values().stream().map(Note::getNoteTitle).toList();
  }

  public String summarize() {
    return super.summarize(true, noteTitles());
  }
}
