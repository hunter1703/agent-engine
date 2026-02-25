package com.agentengine.engine.server.grpc;

import com.agentengine.engine.utils.VirtualThreadExecutors;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.concurrent.ExecutorService;

@Singleton
public final class GrpcVirtualThreadExecutor {
  public static final String NAME_PREFIX = "agent-grpc-vt-";

  private final ExecutorService executor =
      VirtualThreadExecutors.newVirtualThreadExecutor(NAME_PREFIX);

  public ExecutorService executor() {
    return executor;
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }
}
