package com.agentengine.util.distributed;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.locks.Lock;

@Singleton
public class DistributedLockManager {

  private final JgroupsService jgroupsService;

  @Inject
  public DistributedLockManager(JgroupsService jgroupsService) {
    this.jgroupsService = jgroupsService;
  }

  /**
   * Gets a distributed lock across the cluster for the given name.
   *
   * @param name The lock name
   * @return A java.util.concurrent.locks.Lock instance representing the distributed lock
   */
  public Lock getLock(String name) {
    return jgroupsService.getDistributedLock(name);
  }
}
