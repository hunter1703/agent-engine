package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ThreadUtilsTest {

  @Test
  void createsNamedVirtualThreadExecutor() throws Exception {
    ExecutorService executor = ThreadUtils.newVirtualThreadExecutor("test-vt-");
    try {
      CompletableFuture<Thread> captured = new CompletableFuture<>();
      executor.execute(() -> captured.complete(Thread.currentThread()));

      Thread thread = captured.get(5, TimeUnit.SECONDS);
      assertThat(thread.isVirtual()).isTrue();
      assertThat(thread.getName()).startsWith("test-vt-");
    } finally {
      executor.shutdownNow();
    }
  }
}
