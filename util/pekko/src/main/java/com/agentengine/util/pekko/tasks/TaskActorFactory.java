package com.agentengine.util.pekko.tasks;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import com.agentengine.util.common.utils.ThreadUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.actor.AutoPassivableShardedEntityFactory;
import com.agentengine.util.tasks.Task;
import com.agentengine.util.tasks.TaskService;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers the sharded {@link TaskActor}s for a specific task type. */
public class TaskActorFactory<T extends Task>
    extends AutoPassivableShardedEntityFactory<TaskActor.Command> {

  private static final Logger LOG = LoggerFactory.getLogger(TaskActorFactory.class);
  private static final Duration PASSIVATION_TIMEOUT = Duration.ofMinutes(2);

  private final TaskService<T> service;
  private final ExecutorService executor = ThreadUtils.newVirtualThreadExecutor("task-");

  public TaskActorFactory(
      final ActorSystemProvider actorSystemProvider, final TaskService<T> service) {
    super(
        actorSystemProvider,
        EntityTypeKey.create(TaskActor.Command.class, "Task_" + service.taskType()),
        PASSIVATION_TIMEOUT,
        null);
    this.service = service;
  }

  @Override
  protected Behavior<TaskActor.Command> behavior(final EntityContext<TaskActor.Command> context) {
    final String[] segments = context.getEntityId().split(ID_SEPARATOR, 2);
    final String customerId = segments[0];
    return TaskActor.create(Context.asSystemUser(customerId), service, executor);
  }

  /** The id of the actor of one customer's partition of this task type. */
  public static String entityId(final String customerId, final String partitionId) {
    return String.join(ID_SEPARATOR, customerId, partitionId);
  }
}
