package com.agentengine.util.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

public final class ExceptionUtils {

  private ExceptionUtils() {}

  public static String getErrorMessage(final Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    String message = throwable.getMessage();
    if (StringUtils.isBlank(message) && throwable.getCause() != null) {
      return getErrorMessage(throwable.getCause());
    }
    if (StringUtils.isBlank(message)) {
      message = throwable.getClass().getName();
    }
    return message;
  }

  public static Throwable getRootCause(final Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    Throwable current = throwable;
    final Set<Throwable> seen = new HashSet<>();
    while (current.getCause() != null && seen.add(current.getCause())) {
      current = current.getCause();
    }
    return current;
  }

  public static String getStackstrace(final Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    sb.append(throwable).append("\n");
    for (StackTraceElement element : throwable.getStackTrace()) {
      sb.append("\tat ").append(element.toString()).append("\n");
    }
    return sb.toString();
  }

  public static String getFullStackTrace(final Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    final StringWriter sw = new StringWriter();
    final PrintWriter pw = new PrintWriter(sw);
    throwable.printStackTrace(pw);
    return sw.toString();
  }
}
