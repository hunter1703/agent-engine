package com.agentengine.knowledge.core.services;

import com.agentengine.knowledge.api.services.KnowledgeProvisioningService;
import com.agentengine.knowledge.core.repository.KnowledgeMongoStoreClientType;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.knowledge.core.store.KnowledgeVectorStoreClientType;
import com.agentengine.util.mongodb.infra.MongoServerInfraConfig;
import com.agentengine.util.mongodb.mongo.MongoClientProvisioner;
import com.agentengine.util.ms.client.MicroServiceClientInfraConfig;
import com.agentengine.util.ms.client.MicroServiceProvisioner;
import com.agentengine.tenancy.ProvisioningRequest;
import com.agentengine.tenancy.ProvisioningResult;
import com.agentengine.util.ms.client.MicroServiceServerInfraConfig;
import com.agentengine.util.vectordb.VectorDBClientProvisioner;
import com.agentengine.util.vectordb.VectorServerInfraConfig;
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
  public ProvisioningResult provisionEnvironment(final ProvisioningRequest provisioningRequest) {
      return new ProvisioningResult();
  }

  @Override
  public ProvisioningResult provision(final int customerId, final ProvisioningRequest request) {
    final ProvisioningResult result = new ProvisioningResult();
    result.step(
        "mongo",
        () ->
            mongoClientProvisioner.provision(
                KnowledgeMongoStoreClientType.KNOWLEDGE, customerId, request.getServer(MongoServerInfraConfig.TYPE, KnowledgeMongoStoreClientType.KNOWLEDGE.name())));
    result.step(
        "vector",
        () -> vectorDBClientProvisioner.provision(knowledgeChunkStore, customerId, request.getServer(VectorServerInfraConfig.TYPE, KnowledgeVectorStoreClientType.KNOWLEDGE.name())));
    result.step("microservice", () -> microServiceProvisioner.provision(customerId, "knowledge", request.getServer(MicroServiceServerInfraConfig.TYPE, "knowledge")));
    return result;
  }
}
