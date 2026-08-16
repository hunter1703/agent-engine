package com.agentengine.util.scripts.templated;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListTemplateImpl<T> implements Template<List<T>> {

  private final List<Template<T>> templates;

  public ListTemplateImpl(List<Template<T>> templates) {
    this.templates = templates;
  }

  @Override
  public List<T> getValue(Map<String, Object> parameters) {
    final List<T> values = new ArrayList<>();
    for (final Template<T> template : templates) {
      values.add(template.getValue(parameters));
    }
    return values;
  }
}
