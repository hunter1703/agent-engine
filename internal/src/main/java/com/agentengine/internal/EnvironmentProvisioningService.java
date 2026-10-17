package com.agentengine.internal;

import com.agentengine.identity.IdentityDocumentStoreClientType;
import com.agentengine.util.context.Context;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.infra.provisioning.ProvisioningRequest;
import com.agentengine.util.infra.provisioning.ProvisioningResult;
import com.agentengine.util.infra.provisioning.ProvisioningRun;
import com.agentengine.util.infra.provisioning.ProvisioningService;
import com.agentengine.util.mongodb.mongo.MongoClientProvisioner;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Comparator;
import java.util.List;

@Singleton
public class EnvironmentProvisioningService {

  private final Instance<ProvisioningService> provisioningServices;
  private final MongoClientProvisioner mongoClientProvisioner;

  @Inject
  public EnvironmentProvisioningService(
      final Instance<ProvisioningService> provisioningServices,
      final MongoClientProvisioner mongoClientProvisioner) {
    this.provisioningServices = provisioningServices;
    this.mongoClientProvisioner = mongoClientProvisioner;
  }

  public ProvisioningResult provisionEnvironment(final ProvisioningRequest provisioningRequest) {
    final ProvisioningRun run = new ProvisioningRun();
    Context.asSystemCustomer()
        .run(
            () -> {
              run.step(
                  "identity",
                  () ->
                      mongoClientProvisioner.provision(
                          IdentityDocumentStoreClientType.IDENTITY,
                          null,
                          provisioningRequest.getServer(
                              ServerType.MONGO_SERVER,
                              IdentityDocumentStoreClientType.IDENTITY.name())));
              final List<ProvisioningService> orderedServices =
                  provisioningServices.stream()
                      .sorted(Comparator.comparingInt(ProvisioningService::order))
                      .toList();
              for (final ProvisioningService service : orderedServices) {
                run.merge(
                    service.getClass().getSimpleName(),
                    () -> service.provisionEnvironment(provisioningRequest));
              }
            });
    return run.result();
  }
}
