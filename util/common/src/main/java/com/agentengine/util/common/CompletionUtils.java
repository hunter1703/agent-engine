package com.agentengine.util.common;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class CompletionUtils {

  private CompletionUtils() {
  }

  public static <T> CompletionStage<T> completeWithRootCause(final CompletionStage<T> stage) {
    return stage.handle((value, error) -> {
      if (error == null) {
        return CompletableFuture.completedFuture(value);
      }

      final CompletableFuture<T> failed = new CompletableFuture<>();
      failed.completeExceptionally(ExceptionUtils.getRootCause(error));
      return failed;
    }).thenCompose(completionStage -> completionStage);
  }

  public static <T> CompletionStage<T> failedStage(final Throwable throwable) {
    final CompletableFuture<T> failed = new CompletableFuture<>();
    failed.completeExceptionally(throwable);
    return failed;
  }
}
