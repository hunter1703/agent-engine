package com.agentengine.agent.core.services;

import com.agentengine.agent.api.services.AgentProvisioningService;
import com.agentengine.agent.core.memory.AgentVectorStoreClientType;
import com.agentengine.util.agents.repository.AgentDocumentStoreClientType;
import com.agentengine.util.context.Context;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.infra.provisioning.ProvisioningRequest;
import com.agentengine.util.infra.provisioning.ProvisioningResult;
import com.agentengine.util.infra.provisioning.ProvisioningRun;
import com.agentengine.util.mongodb.mongo.MongoClientProvisioner;
import com.agentengine.util.ms.client.MicroServiceProvisioner;
import com.agentengine.util.pekko.persistence.PekkoEventStoreProvisioner;
import com.agentengine.util.pekko.persistence.PekkoUtils;
import com.agentengine.util.vectordb.VectorDBClientProvisioner;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Unremovable
public class AgentProvisioningServiceImpl implements AgentProvisioningService {

  private final MongoClientProvisioner mongoClientProvisioner;
  private final PekkoEventStoreProvisioner pekkoEventStoreProvisioner;
  private final VectorDBClientProvisioner vectorDBClientProvisioner;
  private final MicroServiceProvisioner microServiceProvisioner;

  @Inject
  public AgentProvisioningServiceImpl(
      final MongoClientProvisioner mongoClientProvisioner,
      final PekkoEventStoreProvisioner pekkoEventStoreProvisioner,
      final VectorDBClientProvisioner vectorDBClientProvisioner,
      final MicroServiceProvisioner microServiceProvisioner) {
    this.mongoClientProvisioner = mongoClientProvisioner;
    this.pekkoEventStoreProvisioner = pekkoEventStoreProvisioner;
    this.vectorDBClientProvisioner = vectorDBClientProvisioner;
    this.microServiceProvisioner = microServiceProvisioner;
  }

  @Override
  public ProvisioningResult provisionEnvironment(final ProvisioningRequest request) {
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "event-store",
        () ->
            pekkoEventStoreProvisioner.provision(
                Context.SYSTEM_CUSTOMER_ID,
                request.getServer(ServerType.SQL_SERVER, PekkoUtils.PEKKO_STORE)));
    run.step(
        "microservice",
        () ->
            microServiceProvisioner.provision(
                Context.SYSTEM_CUSTOMER_ID,
                "agent",
                request.getServer(ServerType.MICROSERVICE_SERVER, "agent")));
    return run.result();
  }

  @Override
  public ProvisioningResult provision(final ProvisioningRequest request) {
    final String customerId = Context.requireCustomerId();
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "mongo",
        () ->
            mongoClientProvisioner.provision(
                AgentDocumentStoreClientType.AGENT,
                customerId,
                request.getServer(
                    ServerType.MONGO_SERVER, AgentDocumentStoreClientType.AGENT.name())));
    run.step(
        "event-store",
        () ->
            pekkoEventStoreProvisioner.provision(
                customerId, request.getServer(ServerType.SQL_SERVER, PekkoUtils.PEKKO_STORE)));
    run.step(
        "vector",
        () ->
            vectorDBClientProvisioner.provision(
                AgentVectorStoreClientType.MEMORY,
                customerId,
                request.getServer(
                    ServerType.VECTOR_SERVER, AgentVectorStoreClientType.MEMORY.name())));
    run.step(
        "microservice",
        () ->
            microServiceProvisioner.provision(
                customerId, "agent", request.getServer(ServerType.MICROSERVICE_SERVER, "agent")));
    return run.result();
  }
}
