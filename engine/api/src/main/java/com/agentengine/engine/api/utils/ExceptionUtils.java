package com.agentengine.engine.api.utils;

public final class ExceptionUtils {

  private ExceptionUtils() {
  }

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
}
