package com.agentengine.util.scripts.templated;

import com.agentengine.util.scripts.TemplateUtils;
import java.util.Objects;

public class TemplatedObject<T> implements TemplatedType<T> {
  private final String template;

  public TemplatedObject(String template) {
    Objects.requireNonNull(template, "Non-null template expected");
    this.template = template;
  }

  @Override
  public Template<T> build() {
    return TemplateUtils.buildStringTemplate(template);
  }
}
