package com.agentengine.util.pekko.tasks;

import com.agentengine.util.context.Context;
import com.agentengine.util.pekko.PekkoSerializable;
import com.agentengine.util.tasks.Task;
import com.agentengine.util.tasks.TaskService;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * Processes the tasks of one partition of one task type for one customer, a run at a time, with
 * that task type's service. While idle, a submitted task starts a run; while running, submitted
 * tasks are collected, the latest copy of each, and handled as one batch by the next run, started
 * as soon as the current one finishes. A run that fails leaves its tasks pending, to be submitted
 * again.
 */
public final class TaskActor {

  public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "Task");

  private final ActorContext<Command> context;
  // The system context of the customer whose tasks this actor processes; every run happens in it.
  private final Context customerContext;
  private final TaskService<Task> service;
  private final ExecutorService executor;
  private final Behavior<Command> idle;
  private final Behavior<Command> running;

  // Tasks are equal by id, so a task submitted again replaces its older copy.
  private final Set<Task> submittedTasks = new LinkedHashSet<>();

  private TaskActor(
      final ActorContext<Command> context,
      final Context customerContext,
      final TaskService<Task> service,
      final ExecutorService executor) {
    this.context = context;
    this.customerContext = customerContext;
    this.service = service;
    this.executor = executor;
    this.idle =
        Behaviors.receive(Command.class)
            .onMessage(
                Command.Execute.class,
                execute -> {
                  collect(execute);
                  return startRun();
                })
            .build();
    this.running =
        Behaviors.receive(Command.class)
            .onMessage(
                Command.Execute.class,
                execute -> {
                  collect(execute);
                  return Behaviors.same();
                })
            .onMessage(Command.Finished.class, this::onFinished)
            .build();
  }

  /**
   * The actor processing {@code service}'s tasks of one partition, in {@code customerContext}: the
   * system context of the customer they belong to. Idle until a task arrives.
   */
  @SuppressWarnings("unchecked")
  public static Behavior<Command> create(
      final Context customerContext, final TaskService<?> service, final ExecutorService executor) {
    return Behaviors.setup(
        context ->
            new TaskActor(context, customerContext, (TaskService<Task>) service, executor).idle);
  }

  private Behavior<Command> onFinished(final Command.Finished result) {
    if (result.error() != null) {
      context.getLog().warn("Processing tasks failed; reconciling later: {}", result.error());
    }
    return submittedTasks.isEmpty() ? idle : startRun();
  }

  /** Keeps the latest copy of the submitted task for the next run. */
  private void collect(final Command.Execute execute) {
    submittedTasks.remove(execute.task());
    submittedTasks.add(execute.task());
  }

  /** Hands the collected tasks to a run on the executor, and waits for it to finish. */
  private Behavior<Command> startRun() {
    final List<Task> tasks = List.copyOf(submittedTasks);
    submittedTasks.clear();
    context.pipeToSelf(
        CompletableFuture.runAsync(
            () ->
                customerContext.run(
                    () -> {
                      service.handle(tasks);
                      tasks.forEach(service::markDone);
                    }),
            executor),
        (_, error) -> new Command.Finished(error == null ? null : error.toString()));
    return running;
  }

  public sealed interface Command extends PekkoSerializable {

    /** Submits {@code task} to be processed. */
    record Execute(@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS) Task task) implements Command {}

    /** A run ended; {@code error} describes its failure, or is null. */
    record Finished(String error) implements Command {}
  }
}
