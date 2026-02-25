package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.agents.InvocationContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ViolationUtils {

  public static final String VIOLATIONS_KEY = "violations";

  private ViolationUtils() {}

  public static void addViolation(final InvocationContext context, final Violation violation) {
    if (context == null || violation == null) {
      return;
    }
    context.session().state().compute(VIOLATIONS_KEY, (key, oldVal) -> {
      @SuppressWarnings("unchecked")
      final List<Violation> violations =
          oldVal == null ? new ArrayList<>() : (List<Violation>) oldVal;
      violations.add(violation);
      return violations;
    });
  }

  public static void addViolations(final InvocationContext context, final List<Violation> violations) {
    if (context == null || CollectionUtils.isEmpty(violations)) {
      return;
    }
    context.session().state().compute(VIOLATIONS_KEY, (key, oldVal) -> {
      @SuppressWarnings("unchecked")
      final List<Violation> list =
          oldVal == null ? new ArrayList<>() : (List<Violation>) oldVal;
      list.addAll(violations);
      return list;
    });
  }

  @SuppressWarnings("unchecked")
  public static List<Violation> getViolations(final InvocationContext context) {
    if (context == null) {
      return Collections.emptyList();
    }
    final Object stored = context.session().state().get(VIOLATIONS_KEY);
    if (stored instanceof List<?>) {
      return (List<Violation>) stored;
    }
    return Collections.emptyList();
  }

  public static void clearViolations(final InvocationContext context) {
    if (context != null) {
      context.session().state().remove(VIOLATIONS_KEY);
    }
  }
}
