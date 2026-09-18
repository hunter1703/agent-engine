package com.agentengine.util.common;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.ThreadFactory;
import java.util.function.Predicate;
import java.util.stream.Stream;

@SuppressWarnings("ALL")
public final class StructuredConcurrencyUtils {
  private static final String DEFAULT_TASKS_NAME = "structured-task";

  private StructuredConcurrencyUtils() {}

  public static <T> List<T> runConcurrently(final List<? extends Callable<T>> tasks) {
    return runConcurrently(DEFAULT_TASKS_NAME, tasks);
  }

  /** {@code tasksName} prefixes the names of the threads the tasks run on. */
  public static <T> List<T> runConcurrently(
      final String tasksName, final List<? extends Callable<T>> tasks) {
    final List<TaskOutcome<T>> outcomes = runConcurrentlyUntil(tasksName, tasks, subtask -> false);
    final List<T> results = new ArrayList<>(outcomes.size());
    for (final TaskOutcome<T> outcome : outcomes) {
      failIfNeeded(outcome);
      results.add(outcome.value());
    }
    return results;
  }

  public static <T> List<TaskOutcome<T>> runConcurrentlyUntil(
      final List<? extends Callable<T>> tasks,
      final Predicate<Subtask<? extends T>> stopCondition) {
    return runConcurrentlyUntil(DEFAULT_TASKS_NAME, tasks, stopCondition);
  }

  /**
   * Runs every task concurrently and returns one outcome per task, in task order, whether it
   * succeeded or failed — a failed task never throws here. {@code tasksName} prefixes the names of
   * the threads the tasks run on.
   */
  public static <T> List<TaskOutcome<T>> runConcurrentlyUntil(
      final String tasksName,
      final List<? extends Callable<T>> tasks,
      final Predicate<Subtask<? extends T>> stopCondition) {
    if (CollectionUtils.isEmpty(tasks)) {
      return List.of();
    }

    final Joiner<T, Stream<Subtask<T>>> joiner = Joiner.allUntil(stopCondition);
    final ThreadFactory threadFactory = Thread.ofVirtual().name(tasksName + "-", 0).factory();

    try (StructuredTaskScope<T, Stream<Subtask<T>>> scope =
        StructuredTaskScope.open(
            joiner, config -> config.withName(tasksName).withThreadFactory(threadFactory))) {
      final List<Subtask<T>> subtasks = new ArrayList<>(tasks.size());
      for (final Callable<T> task : tasks) {
        subtasks.add(scope.fork(task));
      }
      scope.join();
      final List<TaskOutcome<T>> outcomes = new ArrayList<>(subtasks.size());
      for (int index = 0; index < subtasks.size(); index++) {
        final Subtask<T> subtask = subtasks.get(index);
        outcomes.add(toOutcome(index, subtask));
      }
      return outcomes;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Structured concurrent execution was interrupted.", ex);
    }
  }

  private static <T> TaskOutcome<T> toOutcome(final int index, final Subtask<T> task) {
    return switch (task.state()) {
      case SUCCESS -> new TaskOutcome<>(index, task.state(), task.get(), null);
      case FAILED -> new TaskOutcome<>(index, task.state(), null, task.exception());
      case UNAVAILABLE -> new TaskOutcome<>(index, task.state(), null, null);
    };
  }

  private static <T> void failIfNeeded(final TaskOutcome<T> outcome) {
    if (outcome.state() == Subtask.State.SUCCESS) {
      return;
    }
    if (outcome.state() == Subtask.State.FAILED) {
      final Throwable throwable = outcome.error();
      if (throwable != null) {
        throw new RuntimeException(throwable);
      }
      throw new IllegalStateException("Structured subtask failed with unknown throwable type.");
    }
    throw new IllegalStateException("Structured subtask did not complete successfully.");
  }

  public record TaskOutcome<T>(int index, Subtask.State state, T value, Throwable error) {}
}
