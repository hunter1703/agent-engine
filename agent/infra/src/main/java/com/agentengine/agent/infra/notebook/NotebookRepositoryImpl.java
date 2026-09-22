package com.agentengine.agent.infra.notebook;

import com.agentengine.util.agents.repository.AgentMongoStoreClientType;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class NotebookRepositoryImpl extends AbstractMongoRepository<Notebook>
    implements NotebookRepository {

  @Inject
  public NotebookRepositoryImpl(
      final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
    super(mongoClientFactory, AgentMongoStoreClientType.AGENT, Notebook.class, validationService);
  }
}
