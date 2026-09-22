package com.agentengine.agent.core.services;

import com.agentengine.agent.api.services.AgentProvisioningService;
import com.agentengine.agent.core.memory.AgentVectorStoreClientType;
import com.agentengine.agent.core.memory.MemoryStore;
import com.agentengine.tenancy.ProvisioningRequest;
import com.agentengine.tenancy.ProvisioningResult;
import com.agentengine.tenancy.ProvisioningRun;
import com.agentengine.util.agents.repository.AgentMongoStoreClientType;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.UserContext;
import com.agentengine.util.infra.ServerType;
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
  private final MemoryStore memoryStore;
  private final MicroServiceProvisioner microServiceProvisioner;

  @Inject
  public AgentProvisioningServiceImpl(
      final MongoClientProvisioner mongoClientProvisioner,
      final PekkoEventStoreProvisioner pekkoEventStoreProvisioner,
      final VectorDBClientProvisioner vectorDBClientProvisioner,
      final MemoryStore memoryStore,
      final MicroServiceProvisioner microServiceProvisioner) {
    this.mongoClientProvisioner = mongoClientProvisioner;
    this.pekkoEventStoreProvisioner = pekkoEventStoreProvisioner;
    this.vectorDBClientProvisioner = vectorDBClientProvisioner;
    this.memoryStore = memoryStore;
    this.microServiceProvisioner = microServiceProvisioner;
  }

  @Override
  public ProvisioningResult provisionEnvironment(final ProvisioningRequest request) {
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "event-store",
        () ->
            pekkoEventStoreProvisioner.provision(
                UserContext.SYSTEM.customerId(),
                request.getServer(ServerType.SQL_SERVER, PekkoUtils.PEKKO_STORE)));
    run.step(
        "microservice",
        () ->
            microServiceProvisioner.provision(
                UserContext.SYSTEM.customerId(),
                "agent",
                request.getServer(ServerType.MICROSERVICE_SERVER, "agent")));
    return run.result();
  }

  @Override
  public ProvisioningResult provision(final ProvisioningRequest request) {
    final int customerId = Context.requireCustomerId();
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "mongo",
        () ->
            mongoClientProvisioner.provision(
                AgentMongoStoreClientType.AGENT,
                customerId,
                request.getServer(
                    ServerType.MONGO_SERVER, AgentMongoStoreClientType.AGENT.name())));
    run.step(
        "event-store",
        () ->
            pekkoEventStoreProvisioner.provision(
                customerId, request.getServer(ServerType.SQL_SERVER, PekkoUtils.PEKKO_STORE)));
    run.step(
        "vector",
        () ->
            vectorDBClientProvisioner.provision(
                memoryStore,
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
