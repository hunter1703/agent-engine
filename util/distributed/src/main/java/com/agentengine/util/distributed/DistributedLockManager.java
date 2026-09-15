package com.agentengine.util.distributed;

import java.time.Duration;
import java.util.concurrent.locks.Lock;

/** Interface for acquiring distributed locks across the cluster. */
public interface DistributedLockManager {
  /**
   * Gets a distributed lock across the cluster for the given name.
   *
   * @param name The lock name
   * @return A java.util.concurrent.locks.Lock instance representing the distributed lock
   */
  Lock getLock(String name);

  /**
   * Gets a distributed lock across the cluster for the given name with a Time-To-Live. Note:
   * Depending on the underlying implementation, TTL might be simulated or ignored if the protocol
   * natively ties lock leases to node lifecycles.
   *
   * @param name The lock name
   * @param ttl The maximum time to live for the lock
   * @return A java.util.concurrent.locks.Lock instance representing the distributed lock
   */
  Lock getLock(String name, Duration ttl);
}
