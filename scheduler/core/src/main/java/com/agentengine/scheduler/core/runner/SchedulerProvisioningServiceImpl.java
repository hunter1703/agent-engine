package com.agentengine.scheduler.core.runner;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.runner.CustomerJobsProvider;
import com.agentengine.scheduler.api.runner.GlobalJobsProvider;
import com.agentengine.scheduler.api.runner.SchedulerProvisioningService;
import com.agentengine.scheduler.api.runner.SchedulerService;
import com.agentengine.scheduler.core.store.SchedulerDocumentStoreClientType;
import com.agentengine.util.context.Context;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.infra.provisioning.ProvisioningRequest;
import com.agentengine.util.infra.provisioning.ProvisioningResult;
import com.agentengine.util.infra.provisioning.ProvisioningRun;
import com.agentengine.util.mongodb.mongo.MongoClientProvisioner;
import com.agentengine.util.ms.client.MicroServiceProvisioner;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Unremovable
public class SchedulerProvisioningServiceImpl implements SchedulerProvisioningService {

  private final MongoClientProvisioner mongoClientProvisioner;
  private final MicroServiceProvisioner microServiceProvisioner;
  private final SchedulerService schedulerService;
  private final Instance<CustomerJobsProvider> customerJobsProviders;
  private final Instance<GlobalJobsProvider> globalJobsProviders;

  @Inject
  public SchedulerProvisioningServiceImpl(
      final MongoClientProvisioner mongoClientProvisioner,
      final MicroServiceProvisioner microServiceProvisioner,
      final SchedulerService schedulerService,
      final Instance<CustomerJobsProvider> customerJobsProviders,
      final Instance<GlobalJobsProvider> globalJobsProviders) {
    this.mongoClientProvisioner = mongoClientProvisioner;
    this.microServiceProvisioner = microServiceProvisioner;
    this.schedulerService = schedulerService;
    this.customerJobsProviders = customerJobsProviders;
    this.globalJobsProviders = globalJobsProviders;
  }

  /**
   * Runs ahead of every other service's environment provisioning: a service that schedules a job as
   * part of its own environment provisioning needs this one's job store (mongo) and microservice
   * registration to already exist first.
   */
  @Override
  public int order() {
    return -100;
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
    run.step("jobs", this::scheduleGlobalJobs);
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
   * Schedules every {@link CustomerJobsProvider}'s jobs for the customer. A job's id is stable per
   * customer, so provisioning again sets a stored job to its template, whatever it was.
   */
  private void scheduleCustomerJobs(final String customerId) {
    for (final CustomerJobsProvider provider : customerJobsProviders) {
      for (final JobDefinition definition : provider.jobDefinitionsFor(customerId)) {
        schedulerService.scheduleIgnoringVersion(definition);
      }
    }
  }

  /**
   * Schedules every {@link GlobalJobsProvider}'s jobs, run as the system customer since this runs
   * during environment provisioning.
   */
  private void scheduleGlobalJobs() {
    for (final GlobalJobsProvider provider : globalJobsProviders) {
      for (final JobDefinition definition : provider.jobDefinitions()) {
        schedulerService.scheduleIgnoringVersion(definition);
      }
    }
  }
}
