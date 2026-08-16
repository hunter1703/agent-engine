package com.agentengine.util.pekko.actor;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;

public interface ShardedEntityDefinition {
  <M, E> Entity<M, E> entity(ActorSystem<?> system);
}
