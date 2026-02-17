package com.agentengine.engine.api.utils;

public final class StringUtils {

  private StringUtils() {
  }

  public static boolean isBlank(final String str) {
    return str == null || str.isBlank();
  }

  public static boolean isNotBlank(final String str) {
    return !isBlank(str);
  }

  public static String substring(final String str, int start, int end) {
    if (str == null) {
      return null;
    }
    if (end < 0) {
      end = str.length() + end;
    }
    if (start < 0) {
      start = str.length() + start;
    }
    if (end > str.length()) {
      end = str.length();
    }
    if (start > end) {
      return "";
    }
    if (start < 0) {
      start = 0;
    }
    if (end < 0) {
      end = 0;
    }
    return str.substring(start, end);
  }
}
