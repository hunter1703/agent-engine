package com.agentengine.util.pekko.actor;

import java.util.Map;
import java.util.Set;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.cluster.sharding.ShardCoordinator;
import scala.collection.immutable.IndexedSeq;
import scala.concurrent.Future;

/**
 * Shard allocation strategy that places a newly-created shard on the cluster member that first
 * addressed it, and never rebalances it away.
 *
 * <p>Whichever pod sends the first message to a given shard entity wins local placement — the
 * entity actor is created in the same JVM as that pod, eliminating one network hop for delivery.
 * With the subscribe-before-publish contract, this naturally co-locates broadcasters with their
 * subscribers.
 */
public final class RequesterFirstAllocationStrategy extends ShardCoordinator.AbstractShardAllocationStrategy {

    public static final RequesterFirstAllocationStrategy INSTANCE = new RequesterFirstAllocationStrategy();

    private RequesterFirstAllocationStrategy() {}

    @Override
    public Future<ActorRef> allocateShard(
            final ActorRef requester,
            final String shardId,
            final Map<ActorRef, IndexedSeq<String>> currentShardAllocations) {
        return Future.successful(requester);
    }

    @Override
    public Future<Set<String>> rebalance(
            final Map<ActorRef, IndexedSeq<String>> currentShardAllocations, final Set<String> rebalanceInProgress) {
        return Future.successful(Set.of());
    }
}
