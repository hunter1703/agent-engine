package com.agentengine.util.pekko.tasks;

import com.agentengine.util.context.Context;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.tasks.Task;
import com.agentengine.util.tasks.TaskService;

/**
 * A {@link TaskService} whose tasks are processed by one sharded {@link TaskActor} per customer,
 * task type and partition, so runs of a partition never overlap across the cluster. A subclass
 * keeps the tasks; submitting one sends it to its partition's actor.
 */
public abstract class AbstractActorTaskService<T extends Task> implements TaskService<T> {

  private final TaskActorFactory<T> taskActorFactory;

  protected AbstractActorTaskService(final ActorSystemProvider actorSystemProvider) {
    this.taskActorFactory = new TaskActorFactory<>(actorSystemProvider, this);
  }

  /**
   * Sends the task to the actor of its partition for the current customer, which processes it as
   * that customer's system: a stored task is processed whoever submitted it.
   */
  @Override
  public void submit(final T task) {
    final String customerId = Context.require().customerId();
    taskActorFactory
        .entityRef(TaskActorFactory.entityId(customerId, task.partitionId()))
        .tell(new TaskActor.Command.Execute(task));
  }
}
