package com.agentengine.agent.core.services;

import com.agentengine.agent.api.services.AgentProvisioningService;
import com.agentengine.agent.core.memory.AgentVectorStoreClientType;
import com.agentengine.agent.core.memory.MemoryStore;
import com.agentengine.util.agents.repository.AgentMongoStoreClientType;
import com.agentengine.util.context.UserContext;
import com.agentengine.util.mongodb.infra.MongoServerInfraConfig;
import com.agentengine.util.mongodb.mongo.MongoClientProvisioner;
import com.agentengine.util.ms.client.MicroServiceProvisioner;
import com.agentengine.tenancy.ProvisioningRequest;
import com.agentengine.tenancy.ProvisioningResult;
import com.agentengine.util.ms.client.MicroServiceServerInfraConfig;
import com.agentengine.util.pekko.persistence.PekkoEventStoreProvisioner;
import com.agentengine.util.pekko.persistence.PekkoUtils;
import com.agentengine.util.sql.SQLServerInfraConfig;
import com.agentengine.util.sql.SQLServerProvisioner;
import com.agentengine.util.vectordb.VectorDBClientProvisioner;
import com.agentengine.util.vectordb.VectorServerInfraConfig;
import com.agentengine.util.vectordb.VectorStoreClientType;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

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
  public ProvisioningResult provisionEnvironment(final ProvisioningRequest provisioningRequest) {
    final ProvisioningResult result = new ProvisioningResult();
    result.step(
        "event-store",
        () -> pekkoEventStoreProvisioner.provision(UserContext.SYSTEM.customerId(), provisioningRequest.getServer(SQLServerInfraConfig.TYPE, PekkoUtils.PEKKO_STORE)));
    return result;
  }

  @Override
  public ProvisioningResult provision(final int customerId, final ProvisioningRequest request) {
    final ProvisioningResult result = new ProvisioningResult();
    result.step(
        "mongo",
        () ->
            mongoClientProvisioner.provision(
                AgentMongoStoreClientType.AGENT, customerId, request.getServer(MongoServerInfraConfig.TYPE, AgentMongoStoreClientType.AGENT.name())));
    result.step(
        "event-store", () -> pekkoEventStoreProvisioner.provision(customerId, request.getServer(SQLServerInfraConfig.TYPE, PekkoUtils.PEKKO_STORE)));
    result.step(
        "vector",
        () -> vectorDBClientProvisioner.provision(memoryStore, customerId, request.getServer(VectorServerInfraConfig.TYPE, AgentVectorStoreClientType.MEMORY.name())));
    result.step("microservice", () -> microServiceProvisioner.provision(customerId, "agent", request.getServer(MicroServiceServerInfraConfig.TYPE, "agent")));
    return result;
  }
}
