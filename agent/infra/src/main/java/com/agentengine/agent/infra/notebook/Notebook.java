package com.agentengine.agent.infra.notebook;

import com.agentengine.util.common.annotations.Permissioned;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import jakarta.validation.constraints.NotBlank;

@Permissioned(assetClass = AssetClass.NOTEBOOK)
public class Notebook extends BaseEntity {

  @NotBlank private String author;
  @NotBlank private String name;
  private String description;

  public Notebook() {}

  public Notebook(final String author, final String name, final String description) {
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
