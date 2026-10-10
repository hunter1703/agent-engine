package com.agentengine.agent.jobs;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.runner.CustomerJobsProvider;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.ResourceUtils;
import com.agentengine.util.scripts.TemplateUtils;
import com.agentengine.util.scripts.templated.Template;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class AgentCustomerJobsProvider implements CustomerJobsProvider {

  private static final String JOBS_RESOURCE = "agent-jobs.json";

  @Override
  public List<JobDefinition> jobDefinitionsFor(final String customerId) {
    final Template<List<Map<String, Object>>> template =
        TemplateUtils.buildTemplate(
            JsonUtils.fromJson(ResourceUtils.loadResourceAsString(JOBS_RESOURCE), List.class));
    return template.getValue(Map.of("customerId", customerId)).stream()
        .map(definition -> JsonUtils.fromMap(definition, JobDefinition.class))
        .toList();
  }
}
