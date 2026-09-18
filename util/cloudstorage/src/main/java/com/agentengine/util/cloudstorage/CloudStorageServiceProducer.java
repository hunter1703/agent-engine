package com.agentengine.util.cloudstorage;

import com.agentengine.util.cloudstorage.oracle.OracleCloudStorage;
import com.agentengine.util.cloudstorage.s3.S3CloudStorage;
import com.agentengine.util.common.service.CloudStorageService;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class CloudStorageServiceProducer {

  // ApplicationScoped, not Singleton: CDI injects a client proxy for a normal scope like this
  // one, and only invokes the producer method - which resolves the infra config and opens the
  // real client connection - on that proxy's first actual method call. A Singleton bean has no
  // proxy, so it would run this eagerly the moment anything (e.g. a gRPC service scanned at
  // startup) is injected with a CloudStorageService, regardless of whether it's ever used.
  @Produces
  @ApplicationScoped
  @DefaultBean
  public CloudStorageService cloudStorageService(final InfraConfigService infraConfigService) {
    final CloudStorageInfraConfig config =
        infraConfigService.findById(
            CloudStorageInfraConfig.CATEGORY,
            CloudStorageInfraConfig.TYPE,
            CloudStorageInfraConfig.CONFIG_ID);
    final CloudStorageInfraConfig.Provider provider =
        config == null ? CloudStorageInfraConfig.Provider.S3 : config.getProvider();
    return switch (provider) {
      case ORACLE -> new OracleCloudStorage(infraConfigService);
      default -> new S3CloudStorage(infraConfigService);
    };
  }
}
