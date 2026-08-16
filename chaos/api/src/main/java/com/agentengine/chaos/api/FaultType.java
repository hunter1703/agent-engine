package com.agentengine.chaos.api;

import java.util.Locale;

public enum FaultType {
  UNKNOWN,

  // Infrastructure — delegated to Chaos Mesh
  POD_KILL,
  NETWORK_PARTITION,
  NETWORK_LATENCY,
  NETWORK_PACKET_LOSS,
  CLUSTER_PARTITION,
  CPU_STRESS,
  MEMORY_STRESS,
  DISK_STRESS,

  // Persistence — database-level via Toxiproxy
  DATABASE_FAILURE,
  EVENT_JOURNAL_FAILURE,
  SNAPSHOT_STORE_FAILURE,
  QDRANT_FAILURE,

  // External dependencies — via Toxiproxy/WireMock
  LLM_PROVIDER_UNAVAILABLE,
  LLM_PROVIDER_LATENCY,
  CONNECTOR_FAILURE,

  // Actor-level — via BehaviorInterceptor
  MESSAGE_DELAY,
  MESSAGE_DROP;

  public static FaultType valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return FaultType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
