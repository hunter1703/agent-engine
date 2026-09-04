package com.agentengine.agent.infra.notebook;

import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.beans.BaseEntity;

@Index(name = "note_notebook_idx", def = "{'notebookId': 1}")
public class Note extends BaseEntity {
  public static final String FIELD_NOTEBOOK_ID = "notebookId";

  private String notebookId;
  private String noteTitle;
  private String content;

  public Note() {}

  public Note(final String notebookId, final String noteTitle, final String content) {
    super(NotebookUtils.noteId(notebookId, noteTitle));
    this.notebookId = notebookId;
    this.noteTitle = noteTitle;
    this.content = content;
  }

  public String getNotebookId() {
    return notebookId;
  }

  public void setNotebookId(final String notebookId) {
    this.notebookId = notebookId;
  }

  public String getNoteTitle() {
    return noteTitle;
  }

  public void setNoteTitle(final String noteTitle) {
    this.noteTitle = noteTitle;
  }

  public String getContent() {
    return content;
  }

  public void setContent(final String content) {
    this.content = content;
  }
}
