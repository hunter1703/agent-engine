package com.agentengine.knowledge.core.services;

import com.agentengine.knowledge.api.services.KnowledgeProvisioningService;
import com.agentengine.knowledge.core.repository.KnowledgeMongoStoreClientType;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.knowledge.core.store.KnowledgeVectorStoreClientType;
import com.agentengine.tenancy.ProvisioningRequest;
import com.agentengine.tenancy.ProvisioningResult;
import com.agentengine.tenancy.ProvisioningRun;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.UserContext;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.mongodb.mongo.MongoClientProvisioner;
import com.agentengine.util.ms.client.MicroServiceProvisioner;
import com.agentengine.util.vectordb.VectorDBClientProvisioner;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Unremovable
public class KnowledgeProvisioningServiceImpl implements KnowledgeProvisioningService {

  private final MongoClientProvisioner mongoClientProvisioner;
  private final VectorDBClientProvisioner vectorDBClientProvisioner;
  private final KnowledgeChunkStore knowledgeChunkStore;
  private final MicroServiceProvisioner microServiceProvisioner;

  @Inject
  public KnowledgeProvisioningServiceImpl(
      final MongoClientProvisioner mongoClientProvisioner,
      final VectorDBClientProvisioner vectorDBClientProvisioner,
      final KnowledgeChunkStore knowledgeChunkStore,
      final MicroServiceProvisioner microServiceProvisioner) {
    this.mongoClientProvisioner = mongoClientProvisioner;
    this.vectorDBClientProvisioner = vectorDBClientProvisioner;
    this.knowledgeChunkStore = knowledgeChunkStore;
    this.microServiceProvisioner = microServiceProvisioner;
  }

  @Override
  public ProvisioningResult provisionEnvironment(final ProvisioningRequest request) {
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "microservice",
        () ->
            microServiceProvisioner.provision(
                UserContext.SYSTEM.customerId(),
                "knowledge",
                request.getServer(ServerType.MICROSERVICE_SERVER, "knowledge")));
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
                KnowledgeMongoStoreClientType.KNOWLEDGE,
                customerId,
                request.getServer(
                    ServerType.MONGO_SERVER, KnowledgeMongoStoreClientType.KNOWLEDGE.name())));
    run.step(
        "vector",
        () ->
            vectorDBClientProvisioner.provision(
                knowledgeChunkStore,
                customerId,
                request.getServer(
                    ServerType.VECTOR_SERVER, KnowledgeVectorStoreClientType.KNOWLEDGE.name())));
    run.step(
        "microservice",
        () ->
            microServiceProvisioner.provision(
                customerId,
                "knowledge",
                request.getServer(ServerType.MICROSERVICE_SERVER, "knowledge")));
    return run.result();
  }
}
