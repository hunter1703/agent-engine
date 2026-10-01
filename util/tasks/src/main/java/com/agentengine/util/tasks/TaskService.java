package com.agentengine.util.tasks;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * The tasks of one task type: taking them in, handling them, marking them done, and finding those
 * left pending for too long.
 */
public interface TaskService<T extends Task> {

  /** The type of task served here; unique among task services. */
  String taskType();

  /**
   * How long a task may stay pending before it counts as stale — longer than a run normally takes.
   */
  Duration staleAfter();

  /**
   * Has {@code task} processed, in a batch with the other tasks of its partition submitted
   * meanwhile. Returns without waiting; a task whose submission is lost stays pending until it is
   * submitted again.
   */
  void submit(T task);

  /**
   * Handles {@code tasks}, tasks of one partition submitted since the last batch, as a batch, in
   * the system context of their customer. Each task is marked done once this returns; when it
   * throws, every task stays pending.
   */
  void handle(List<T> tasks);

  /** Marks the task done, unless it was written since it was submitted: then it stays pending. */
  void markDone(T task);

  /** The tasks pending since before {@code pendingSince}, epoch millis. */
  Collection<T> findStale(long pendingSince);
}
