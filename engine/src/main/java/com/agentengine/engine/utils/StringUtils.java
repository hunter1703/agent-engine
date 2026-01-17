package com.agentengine.engine.utils;

public final class StringUtils {

  private StringUtils() {}

  public static boolean isBlank(final String str) {
    return str == null || str.isBlank();
  }

  public static boolean isNotBlank(final String str) {
    return !isBlank(str);
  }
}
