package com.agentengine.util.common;

/**
 * Default values shared across layers/modules -- one place, rather than re-declaring the same
 * default independently wherever it's used.
 */
public final class Defaults {

  /**
   * Batching policy for a stream of items flushed in groups rather than one at a time -- shared by
   * every layer that batches a stream for write efficiency (e.g. {@code GRPCServerImpl} batching
   * gRPC responses server-side, REST SSE writers re-batching before a network write), not specific
   * to any one of them.
   */
  public static final int STREAMING_BATCH_SIZE = 100;

  public static final long STREAMING_BATCH_FLUSH_INTERVAL_MS = 50;

  private Defaults() {}
}
