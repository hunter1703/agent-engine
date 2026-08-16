package com.agentengine.util.common.validation;

import com.agentengine.util.common.StringUtils;
import java.util.ArrayList;
import java.util.List;

public final class ValidationCollector {
  private final List<String> errors = new ArrayList<>();

  public void add(final String message) {
    if (StringUtils.isBlank(message)) {
      return;
    }
    errors.add(message.trim());
  }

  public void addAll(final Iterable<String> messages) {
    if (messages == null) {
      return;
    }
    for (final String message : messages) {
      add(message);
    }
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  public List<String> errors() {
    return List.copyOf(errors);
  }
}
