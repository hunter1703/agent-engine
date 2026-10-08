package com.agentengine.util.common.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

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

  public static RuntimeException wrapInRuntimeException(final Throwable throwable) {
    return throwable instanceof RuntimeException runtimeException
        ? runtimeException
        : new RuntimeException(throwable);
  }

  public static RuntimeException wrapInRuntimeException(
      final Throwable throwable, final String message) {
    return throwable instanceof RuntimeException runtimeException
        ? runtimeException
        : new RuntimeException(message, throwable);
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

  /** The root cause's class and message on one line, e.g. {@code EOFException: EOF reached}. */
  public static String getErrorSummary(final Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    final Throwable root = getRootCause(throwable);
    final String message = root.getMessage();
    return StringUtils.isBlank(message)
        ? root.getClass().getSimpleName()
        : root.getClass().getSimpleName() + ": " + message;
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

  /** Whether {@code error} or any of its causes satisfies {@code matches}. */
  public static boolean hasCause(final Throwable error, final Predicate<Throwable> matches) {
    final Set<Throwable> seen = new HashSet<>();
    for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
      if (matches.test(cause)) {
        return true;
      }
    }
    return false;
  }
}
