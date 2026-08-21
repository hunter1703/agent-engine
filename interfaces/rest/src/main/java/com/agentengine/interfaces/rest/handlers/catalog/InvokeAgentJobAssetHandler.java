package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.runner.SchedulerService;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class InvokeAgentJobAssetHandler implements AssetHandler<JobDefinition> {

  public static final String INVOKE_AGENT_JOB_CLASS_NAME =
      "com.agentengine.agent.jobs.InvokeAgentJob";

  private final SchedulerService schedulerService;

  @Inject
  public InvokeAgentJobAssetHandler(final SchedulerService schedulerService) {
    this.schedulerService = schedulerService;
  }

  @Override
  public String getAssetType() {
    return AssetClass.INVOKE_AGENT_JOB;
  }

  @Override
  public PaginatedResult<JobDefinition> findAssets(final AssetRequest request) {
    final Query query = request.getQuery() == null ? new Query() : new Query(request.getQuery());
    final Filter jobClassFilter =
        Filters.eq(JobDefinition.FIELD_JOB_CLASS_NAME, INVOKE_AGENT_JOB_CLASS_NAME);
    query.setFilter(
        query.getFilter() == null
            ? jobClassFilter
            : Filters.and(jobClassFilter, query.getFilter()));
    return schedulerService.findJobs(query);
  }

  @Override
  public Map<String, JobDefinition> getAssetsByIds(final AssetRequest request) {
    return CollectionUtils.nullSafeList(request.getKeys()).stream()
        .map(schedulerService::getJob)
        .filter(Objects::nonNull)
        .filter(job -> INVOKE_AGENT_JOB_CLASS_NAME.equals(job.getJobClassName()))
        .collect(Collectors.toMap(JobDefinition::getId, Function.identity()));
  }
}
