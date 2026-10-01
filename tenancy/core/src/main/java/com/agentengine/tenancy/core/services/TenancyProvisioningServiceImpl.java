package com.agentengine.tenancy.core.services;

import com.agentengine.tenancy.TenancyProvisioningService;
import com.agentengine.tenancy.core.rbac.Role;
import com.agentengine.tenancy.core.repository.RoleRepository;
import com.agentengine.tenancy.core.repository.TenancyDocumentStoreClientType;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.ResourceUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.infra.ServerType;
import com.agentengine.util.infra.provisioning.ProvisioningRequest;
import com.agentengine.util.infra.provisioning.ProvisioningResult;
import com.agentengine.util.infra.provisioning.ProvisioningRun;
import com.agentengine.util.mongodb.mongo.MongoClientProvisioner;
import com.agentengine.util.ms.client.MicroServiceProvisioner;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Unremovable
public class TenancyProvisioningServiceImpl implements TenancyProvisioningService {

  private static final String ROLES_RESOURCE = "roles.json";

  private final MongoClientProvisioner mongoClientProvisioner;
  private final MicroServiceProvisioner microServiceProvisioner;
  private final RoleRepository roleRepository;

  @Inject
  public TenancyProvisioningServiceImpl(
      final MongoClientProvisioner mongoClientProvisioner,
      final MicroServiceProvisioner microServiceProvisioner,
      final RoleRepository roleRepository) {
    this.mongoClientProvisioner = mongoClientProvisioner;
    this.microServiceProvisioner = microServiceProvisioner;
    this.roleRepository = roleRepository;
  }

  @Override
  public ProvisioningResult provisionEnvironment(final ProvisioningRequest request) {
    final ProvisioningRun run = new ProvisioningRun();
    run.step(
        "mongo",
        () ->
            mongoClientProvisioner.provision(
                TenancyDocumentStoreClientType.TENANCY,
                null,
                request.getServer(
                    ServerType.MONGO_SERVER, TenancyDocumentStoreClientType.TENANCY.name())));
    run.step(
        "microservice",
        () ->
            microServiceProvisioner.provision(
                Context.SYSTEM_CUSTOMER_ID,
                "tenancy",
                request.getServer(ServerType.MICROSERVICE_SERVER, "tenancy")));
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
                TenancyDocumentStoreClientType.TENANCY,
                customerId,
                request.getServer(
                    ServerType.MONGO_SERVER, TenancyDocumentStoreClientType.TENANCY.name())));
    run.step("roles", this::saveStandardRoles);
    return run.result();
  }

  private void saveStandardRoles() {
    final Role[] roles =
        JsonUtils.fromJson(ResourceUtils.loadResourceAsString(ROLES_RESOURCE), Role[].class);
    for (final Role role : roles) {
      roleRepository.saveIgnoringVersion(role);
    }
  }
}
