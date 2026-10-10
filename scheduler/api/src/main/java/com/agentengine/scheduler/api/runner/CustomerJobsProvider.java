package com.agentengine.scheduler.api.runner;

import com.agentengine.scheduler.api.models.JobDefinition;
import java.util.List;

/**
 * Contributes this module's own recurring customer-level jobs. Every bean of this type is
 * discovered generically by the scheduler service when it provisions a customer, so a domain
 * module adds its own jobs without {@code scheduler:core} needing to know it exists — the same
 * way an {@code EntityChangeListener} or a {@code ProvisioningService} is discovered.
 */
public interface CustomerJobsProvider {

  List<JobDefinition> jobDefinitionsFor(String customerId);
}
