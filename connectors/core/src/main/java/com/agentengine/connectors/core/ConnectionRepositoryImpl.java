package com.agentengine.connectors.core;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Singleton;

@Singleton
public class ConnectionRepositoryImpl extends AbstractMongoRepository<Connection>
    implements ConnectionRepository {

  public ConnectionRepositoryImpl(
      MongoClientFactory mongoClientFactory, ValidationService validationService) {
    super(mongoClientFactory, AssetClass.CONNECTION, Connection.class, validationService);
  }
}
