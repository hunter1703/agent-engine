package com.agentengine.util.distributed;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.locks.Lock;

@Singleton
public class DistributedSoftLockManager implements DistributedLockManager {

  private final JgroupsService jgroupsService;

  @Inject
  public DistributedSoftLockManager(JgroupsService jgroupsService) {
    this.jgroupsService = jgroupsService;
  }

  @Override
  public Lock getLock(String name) {
    return jgroupsService.getDistributedLock(name);
  }

  @Override
  public Lock getLock(String name, Duration ttl) {
    throw new UnsupportedOperationException("TTL based lock not supported");
  }
}
