package com.agentengine.agent.infra.notebook;

import com.agentengine.util.common.annotations.Permissioned;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import jakarta.validation.constraints.NotBlank;
import java.util.Collection;

@Permissioned(assetClass = AssetClass.NOTEBOOK)
public class Notebook extends BaseEntity {

  public static final String FIELD_AUTHOR = "author";

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

  public String summarize(final boolean canWrite, final Collection<String> noteTitles) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Notebook id: `").append(getId()).append("`\n");
    sb.append("  - name: ").append(getName()).append("\n");
    if (StringUtils.isNotBlank(getDescription())) {
      sb.append("  - description: ").append(getDescription()).append("\n");
    }
    sb.append("  - add, change and delete notes: ")
        .append(canWrite ? "yes" : "no, read only")
        .append("\n");
    sb.append("  - notes:");
    if (CollectionUtils.isEmpty(noteTitles)) {
      sb.append(" (none)");
    } else {
      for (final String noteTitle : noteTitles) {
        sb.append("\n    - `").append(noteTitle).append("`");
      }
    }
    return sb.toString();
  }
}
