package com.agentengine.engine.api.utils;

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
}
