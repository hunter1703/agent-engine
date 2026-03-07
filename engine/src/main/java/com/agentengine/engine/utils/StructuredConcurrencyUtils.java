package com.agentengine.engine.utils;

import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.ArrayList;
import java.util.List;

public final class StructuredConcurrencyUtils {
  private StructuredConcurrencyUtils() {}

  public static <T> List<T> runConcurrently(final List<? extends Callable<T>> tasks) {
    if (tasks == null || tasks.isEmpty()) {
      return List.of();
    }
    try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll())) {
      final List<StructuredTaskScope.Subtask<T>> subtasks = new ArrayList<>(tasks.size());
      for (final Callable<T> task : tasks) {
        subtasks.add(scope.fork(task));
      }
      scope.join();
      for (final StructuredTaskScope.Subtask<T> subtask : subtasks) {
        failIfNeeded(subtask);
      }
      final List<T> results = new ArrayList<>(subtasks.size());
      for (final StructuredTaskScope.Subtask<T> subtask : subtasks) {
        results.add(subtask.get());
      }
      return results;
    } catch (InterruptedException e) {
        throw new RuntimeException(e);
    }
  }

  private static <T> void failIfNeeded(final StructuredTaskScope.Subtask<T> task) {
    if (task.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
      return;
    }
    if (task.state() == StructuredTaskScope.Subtask.State.FAILED) {
      final Throwable throwable = task.exception();
      if (throwable != null) {
        throw new RuntimeException(throwable);
      }
      throw new IllegalStateException("Structured subtask failed with unknown throwable type.");
    }
    throw new IllegalStateException("Structured subtask did not complete successfully.");
  }
}
