package com.agentengine.scheduler.core.runner;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.runner.SchedulerProvisioningService;
import com.agentengine.scheduler.api.runner.SchedulerService;
import com.agentengine.scheduler.core.store.SchedulerDocumentStoreClientType;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.ResourceUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.infra.provisioning.ProvisioningRequest;
import com.agentengine.util.infra.provisioning.ProvisioningResult;
import com.agentengine.util.infra.provisioning.ProvisioningRun;
import com.agentengine.util.mongodb.mongo.MongoClientProvisioner;
import com.agentengine.util.ms.client.MicroServiceProvisioner;
import com.agentengine.util.scripts.TemplateUtils;
import com.agentengine.util.scripts.templated.Template;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
@Unremovable
public class SchedulerProvisioningServiceImpl implements SchedulerProvisioningService {

  // The jobs every customer gets, as templates of their definitions rendered with the customer id.
  private static final String JOBS_RESOURCE = "jobs.json";
  private static final String CUSTOMER_ID_PARAMETER = "customerId";

  private final MongoClientProvisioner mongoClientProvisioner;
  private final MicroServiceProvisioner microServiceProvisioner;
  private final SchedulerService schedulerService;
  private final Template<List<Map<String, Object>>> jobTemplates;

  @Inject
  public SchedulerProvisioningServiceImpl(
      final MongoClientProvisioner mongoClientProvisioner,
      final MicroServiceProvisioner microServiceProvisioner,
      final SchedulerService schedulerService) {
    this.mongoClientProvisioner = mongoClientProvisioner;
    this.microServiceProvisioner = microServiceProvisioner;
    this.schedulerService = schedulerService;
    this.jobTemplates =
        TemplateUtils.buildTemplate(
            JsonUtils.fromJson(ResourceUtils.loadResourceAsString(JOBS_RESOURCE), List.class));
  }

  @Override
  public ProvisioningResult provisionEnvironment(final ProvisioningRequest request) {
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "mongo",
        () ->
            mongoClientProvisioner.provision(
                SchedulerDocumentStoreClientType.SCHEDULER,
                null,
                request.getServer(
                    ServerType.MONGO_SERVER, SchedulerDocumentStoreClientType.SCHEDULER.name())));
    run.step(
        "microservice",
        () ->
            microServiceProvisioner.provision(
                Context.SYSTEM_CUSTOMER_ID,
                "scheduler",
                request.getServer(ServerType.MICROSERVICE_SERVER, "scheduler")));
    return run.result();
  }

  @Override
  public ProvisioningResult provision(final ProvisioningRequest request) {
    final String customerId = Context.requireCustomerId();
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "microservice",
        () ->
            microServiceProvisioner.provision(
                customerId,
                "scheduler",
                request.getServer(ServerType.MICROSERVICE_SERVER, "scheduler")));
    run.step("jobs", () -> scheduleCustomerJobs(customerId));
    return run.result();
  }

  /**
   * Schedules the customer's jobs as {@value #JOBS_RESOURCE} defines them. A job's id is stable per
   * customer, so provisioning again sets a stored job to its template, at the version stored.
   */
  private void scheduleCustomerJobs(final String customerId) {
    for (final Map<String, Object> definition :
        jobTemplates.getValue(Map.of(CUSTOMER_ID_PARAMETER, customerId))) {
      final JobDefinition job = JsonUtils.fromMap(definition, JobDefinition.class);
      final JobDefinition existingJob = schedulerService.getJob(job.getId());
      if (existingJob != null) {
        job.setVersion(existingJob.getVersion());
      }
      schedulerService.schedule(job);
    }
  }
}
