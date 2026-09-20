package com.agentengine.internal;

import com.agentengine.util.cloudstorage.CloudStorageServerInfraConfig;
import com.agentengine.util.cloudstorage.CloudStorageServerProvisioner;
import com.agentengine.util.context.ContextAware;
import com.agentengine.util.crypto.EncryptionKeyInfraConfig;
import com.agentengine.util.crypto.EncryptionServerProvisioner;
import com.agentengine.util.infra.InfraConfig;
import com.agentengine.util.mongodb.infra.MongoServerInfraConfig;
import com.agentengine.util.mongodb.mongo.MongoServerProvisioner;
import com.agentengine.util.ms.client.MicroServiceServerInfraConfig;
import com.agentengine.util.ms.client.MicroServiceServerProvisioner;
import com.agentengine.util.sql.SQLServerInfraConfig;
import com.agentengine.util.sql.SQLServerProvisioner;
import com.agentengine.util.vectordb.VectorServerInfraConfig;
import com.agentengine.util.vectordb.VectorDBServerProvisioner;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@ContextAware
@Path("/server")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ServerConfigIntRestAPI {

  private final MongoServerProvisioner mongoServerProvisioner;
  private final SQLServerProvisioner sqlServerProvisioner;
  private final VectorDBServerProvisioner vectorDBServerProvisioner;
  private final CloudStorageServerProvisioner cloudStorageServerProvisioner;
  private final EncryptionServerProvisioner encryptionServerProvisioner;
  private final MicroServiceServerProvisioner microServiceServerProvisioner;

  @Inject
  public ServerConfigIntRestAPI(
      final MongoServerProvisioner mongoServerProvisioner,
      final SQLServerProvisioner sqlServerProvisioner,
      final VectorDBServerProvisioner vectorDBServerProvisioner,
      final CloudStorageServerProvisioner cloudStorageServerProvisioner,
      final EncryptionServerProvisioner encryptionServerProvisioner,
      final MicroServiceServerProvisioner microServiceServerProvisioner) {
    this.mongoServerProvisioner = mongoServerProvisioner;
    this.sqlServerProvisioner = sqlServerProvisioner;
    this.vectorDBServerProvisioner = vectorDBServerProvisioner;
    this.cloudStorageServerProvisioner = cloudStorageServerProvisioner;
    this.encryptionServerProvisioner = encryptionServerProvisioner;
    this.microServiceServerProvisioner = microServiceServerProvisioner;
  }

  @PUT
  @Path("/mongo")
  public InfraConfig saveMongo(final MongoServerInfraConfig server) {
    return mongoServerProvisioner.provision(server);
  }

  @PUT
  @Path("/sql")
  public InfraConfig saveSql(final SQLServerInfraConfig server) {
    return sqlServerProvisioner.provision(server);
  }

  @PUT
  @Path("/vector")
  public InfraConfig saveVector(final VectorServerInfraConfig server) {
    return vectorDBServerProvisioner.provision(server);
  }

  @PUT
  @Path("/cloudstorage")
  public InfraConfig saveCloudStorage(final CloudStorageServerInfraConfig server) {
    return cloudStorageServerProvisioner.provision(server);
  }

  @PUT
  @Path("/encryption")
  public InfraConfig saveEncryption(final EncryptionKeyInfraConfig server) {
    return encryptionServerProvisioner.provision(server);
  }

  @PUT
  @Path("/microservice")
  public InfraConfig saveMicroService(final MicroServiceServerInfraConfig server) {
    return microServiceServerProvisioner.provision(server);
  }
}
