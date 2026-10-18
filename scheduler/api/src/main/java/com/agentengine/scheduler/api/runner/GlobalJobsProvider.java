package com.agentengine.scheduler.api.runner;

import com.agentengine.scheduler.api.models.JobDefinition;
import java.util.List;

/**
 * Contributes this module's own jobs that run once per environment rather than once per tenant —
 * e.g. a job seeded under the system customer at environment-provisioning time. Every bean of
 * this type is discovered generically by the scheduler service the same way a {@link
 * CustomerJobsProvider} is, so a domain module adds its own jobs without {@code scheduler:core}
 * needing to know it exists.
 */
public interface GlobalJobsProvider {

  List<JobDefinition> jobDefinitions();
}
