package com.agentengine.util.pekko.tasks;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.ThreadUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.actor.AutoPassivableShardedEntityFactory;
import com.agentengine.util.tasks.TaskService;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers the sharded {@link TaskActor}s, one per customer, task type and partition. */
@Singleton
@Unremovable
public class TaskActorFactory extends AutoPassivableShardedEntityFactory<TaskActor.Command> {

  private static final Logger LOG = LoggerFactory.getLogger(TaskActorFactory.class);
  private static final Duration PASSIVATION_TIMEOUT = Duration.ofMinutes(2);

  // Built on first use, since a task service depends on this factory to submit its tasks.
  private final LazyLoader<Map<String, TaskService<?>>> taskTypeVsService;
  private final ExecutorService executor = ThreadUtils.newVirtualThreadExecutor("task-");

  @Inject
  public TaskActorFactory(
      final ActorSystemProvider actorSystemProvider, final Instance<TaskService<?>> services) {
    super(actorSystemProvider, TaskActor.TYPE_KEY, PASSIVATION_TIMEOUT, null);
    this.taskTypeVsService =
        new LazyLoader<>(
            () ->
                CollectionUtils.transformToMap(services.stream().toList(), TaskService::taskType));
  }

  @Override
  protected Behavior<TaskActor.Command> behavior(final EntityContext<TaskActor.Command> context) {
    final String[] segments = context.getEntityId().split(ID_SEPARATOR, 3);
    final String customerId = segments[0];
    final String taskType = segments[1];
    final TaskService<?> service = taskTypeVsService.get().get(taskType);
    if (service == null) {
      LOG.error("No task service for task type {}; ignoring its tasks", taskType);
      return Behaviors.ignore();
    }
    return TaskActor.create(Context.asSystemUser(customerId), service, executor);
  }

  /** The id of the actor of one customer's partition of one task type. */
  public static String entityId(
      final String customerId, final String taskType, final String partitionId) {
    return String.join(ID_SEPARATOR, customerId, taskType, partitionId);
  }
}
