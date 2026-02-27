package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.StringUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class ThreadUtils {
  private static final String DEFAULT_PREFIX = "virtual-thread-";

  private ThreadUtils() {}

  public static ExecutorService newVirtualThreadExecutor(final String namePrefix) {
    final String resolvedPrefix =
        StringUtils.isBlank(namePrefix) ? DEFAULT_PREFIX : namePrefix.trim();
    final ThreadFactory factory = Thread.ofVirtual().name(resolvedPrefix, 0).factory();
    return Executors.newThreadPerTaskExecutor(factory);
  }
}
