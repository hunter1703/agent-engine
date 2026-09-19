package com.agentengine.agent.infra.notebook;

import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.agents.repository.AgentMongoStoreClientType;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class NotesRepositoryImpl extends AbstractMongoRepository<Note> implements NotesRepository {

  @Inject
  public NotesRepositoryImpl(
      final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
    super(mongoClientFactory, AgentMongoStoreClientType.AGENT, Note.class, validationService);
  }
}
