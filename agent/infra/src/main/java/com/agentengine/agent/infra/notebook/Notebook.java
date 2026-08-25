package com.agentengine.agent.infra.notebook;

import com.agentengine.agent.infra.tools.notebook.NotebookUtils;
import com.agentengine.util.common.beans.BaseEntity;

public class Notebook extends BaseEntity {

  private String author;
  private String name;
  private String description;

  public Notebook() {}

  public Notebook(final String author, final String name, final String description) {
    super(NotebookUtils.notebookId(author, name));
    this.author = author;
    this.name = name;
    this.description = description;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(final String author) {
    this.author = author;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }
}
