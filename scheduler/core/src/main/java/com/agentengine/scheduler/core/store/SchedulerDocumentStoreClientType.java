package com.agentengine.scheduler.core.store;

import com.agentengine.util.common.repository.DocumentStoreClientType;
import java.util.Locale;

public enum SchedulerDocumentStoreClientType implements DocumentStoreClientType {
  SCHEDULER,
  UNKNOWN;

  public static SchedulerDocumentStoreClientType valueOfOrDefault(final String value) {
    if (value == null) {
      return UNKNOWN;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
